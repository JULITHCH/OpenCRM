# ADR-006: Import/Export mit Spring Batch und PostgreSQL-Job-Tabellen

| Status | Datum | Entscheider |
|---|---|---|
| Akzeptiert | 2026-07-16 | Lead Architecture |

## Kontext

OpenCRM muss Massen-Importe (Leads, Accounts, Contacts, Products aus CSV/XLSX) und Exporte (CSV/XLSX/JSON) asynchron verarbeiten. Anforderungen an die Verarbeitung:

- Dateien mit zehntausenden Zeilen, Validierung mit Dry-Run (`mode = DRY_RUN`), Duplikatstrategien `SKIP`/`UPDATE`/`CREATE`,
- Fehlerbericht je Zeile (`import_job_errors` mit `row_number`, `error_code`, `raw_row`) und Fortschrittsanzeige waehrend des Laufs,
- Wiederaufnahme nach Abbruch (Pod-Restart, Deployment) ohne Duplikate oder Datenverlust,
- zusaetzlich der periodische Dashboard-Refresh (`mv_sales_kpis_daily`, alle 15 Minuten) als geplanter Hintergrundjob.

Die Baseline schliesst Message-Broker und Redis fuer Phase 1 bewusst aus. Fachlicher Ablauf und Job-Modell (`import_jobs`, `import_job_errors`, `import_mappings`, `export_jobs`) sind in [08-import-export.md](../08-import-export.md) beschrieben; Dateien liegen im S3-kompatiblen Objekt-Storage.

## Entscheidung

Wir verarbeiten Import/Export mit **Spring Batch**, Zustandsfuehrung ausschliesslich in **PostgreSQL**:

- Je Vorgang ein Spring-Batch-Job (chunk-orientiert: Reader auf Datei im Objekt-Storage, Processor fuer Validierung/Mapping/Duplikatstrategie, Writer per JPA/JDBC); Spring-Batch-Metadaten (`BATCH_JOB_INSTANCE` usw.) liegen in derselben PostgreSQL-Datenbank.
- Die fachlichen Tabellen `import_jobs`/`export_jobs` fuehren den nutzer-sichtbaren Zustand (Status `PENDING` bis `COMPLETED`/`COMPLETED_WITH_ERRORS`/`FAILED`/`CANCELLED`, Fortschrittszaehler `total_rows`/`processed_rows`/`error_rows`); die API liest ausschliesslich diese Tabellen.
- Job-Anstoss ueber die REST-API (Insert in die Job-Tabelle), Ausfuehrung ueber einen Scheduler/Poller im Backend; Dispatch mit `SELECT ... FOR UPDATE SKIP LOCKED`, damit bei mehreren Replicas jeder Job genau einmal laeuft.
- Jeder Import-Job setzt den Tenant-Kontext (`SET LOCAL app.current_tenant`) aus `import_jobs.tenant_id`, RLS bleibt aktiv ([ADR-002](ADR-002-multi-tenancy-shared-schema-rls.md)).
- Dashboard-Refresh laeuft als geplanter Job im selben Mechanismus.

## Konsequenzen

### Positiv

- **Weniger Infrastruktur**: keine zusaetzliche Betriebs-Komponente (Broker-Cluster, Redis) fuer Phase 1 – weniger Deployment-, Monitoring- und Ausfall-Flaechen, schnellerer Projektstart; konsistent mit [ADR-001](ADR-001-postgresql-als-datenbank.md) und [ADR-004](ADR-004-modularer-monolith-spring-boot.md).
- **Batch-Semantik inklusive Restart**: Spring Batch liefert Chunking mit Commit-Intervallen, Skip-/Retry-Policies je Zeile und Wiederaufnahme eines abgebrochenen Jobs am letzten Commit-Punkt – genau die Semantik, die zeilenweise Datei-Importe brauchen und die ein Broker nicht mitbringt.
- Jobstatus, Fortschritt und Fehlerzeilen sind transaktional konsistent mit den importierten Daten (eine Datenbank, eine Transaktion je Chunk).
- Dry-Run (`mode = DRY_RUN`) ist derselbe Job ohne Writer-Commit – kein doppelter Codepfad.
- **Klarer Migrationspfad**: Die API kennt nur die Job-Tabellen. Der Dispatch-Mechanismus (Poller) laesst sich spaeter durch einen Broker-Consumer ersetzen, ohne API, Datenmodell oder Job-Logik zu aendern.

### Negativ

- Polling statt Push: Latenz zwischen Job-Anlage und Start entspricht dem Poll-Intervall (Sekundenbereich, fuer Batch-Vorgaenge akzeptabel); Polling erzeugt Grundlast auf der DB.
- Durchsatzgrenze: Job-Verarbeitung konkurriert mit dem Online-Betrieb um DB-Verbindungen und JVM-Ressourcen; Mitigation: begrenzte Worker-Threads, Chunk-Groessen, ggf. spaeter dediziertes Worker-Deployment.
- Kein Fan-out an externe Konsumenten: andere Systeme koennen Job-Ereignisse nicht abonnieren (fuer Phase 1 nicht gefordert; Webhooks/Broker sind Phase-2-Kandidaten, siehe [12-roadmap.md](../12-roadmap.md)).
- Spring-Batch-Metadatentabellen wachsen und brauchen einen Aufraeumjob.

## Betrachtete Alternativen

### Message-Broker (Kafka)

Kafka als Job- und Event-Backbone. Abgelehnt fuer Phase 1: Ein Kafka-Cluster (plus Schema-Verwaltung, Offsets, Partitionierung) ist erheblicher Betriebsaufwand fuer ein Team von 4–6 Personen und loest das falsche Problem – wir brauchen wiederaufsetzbare Datei-Batch-Verarbeitung mit Zeilenfehler-Berichten, nicht Event-Streaming mit hohem Durchsatz. Restart-Semantik und Fortschrittsanzeige muessten auf Kafka-Basis selbst gebaut werden, was Spring Batch fertig liefert.

### Message-Broker (RabbitMQ)

Leichter als Kafka, klassische Work-Queues. Abgelehnt, weil auch RabbitMQ eine zusaetzliche HA-Komponente mit eigenem Monitoring und Failover-Konzept ist, waehrend der konkrete Bedarf – wenige gleichzeitige, langlaufende Jobs je Tenant – von einem DB-basierten Dispatch mit `SKIP LOCKED` vollstaendig abgedeckt wird. Die Batch-Anforderungen (Chunk-Restart, Skip-Policies, Dry-Run) lägen weiterhin bei Spring Batch, RabbitMQ waere nur ein teurerer Ausloesemechanismus.

### Eigenbau-Jobrunner ohne Spring Batch

Einfacher `@Scheduled`-Worker, der Zeilen in einer Schleife verarbeitet. Abgelehnt, weil Restart nach Abbruch, Commit-Intervalle, Skip-/Retry-Verhalten und Metadaten-Fuehrung dann selbst implementiert und getestet werden muessten – exakt der gehaertete Kern von Spring Batch. Der Eigenbau spart initial wenig und kostet bei jedem Randfall (OOM mitten im Import, Pod-Restart) Nacharbeit.

## Offene Punkte

Alle offenen Punkte sind entschieden oder terminiert (Stand 2026-07-16) — Details im [Entscheidungsprotokoll](../13-entscheidungen.md).

1. Poll-Intervall und Parallelitaet → **E-68**: Poll-Intervall 5 s; max. 2 parallele Import-Jobs je Tenant, 4 je Instanz; Kalibrierung unter Last in M2.
2. Aufbewahrungsfristen → **E-69**: `import_job_errors` 90 Tage, Spring-Batch-Metadaten 30 Tage, Dateien im Objekt-Storage 30 Tage; taeglicher Cleanup-Job.
3. Broker-Wechselkriterium → **E-70**: Job-Wartezeit p95 > 5 min ueber 7 Tage trotz Worker-Deployment oder Event-Bedarf durch Webhooks.
