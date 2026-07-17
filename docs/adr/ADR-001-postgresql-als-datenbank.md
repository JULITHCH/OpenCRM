# ADR-001: PostgreSQL als Datenbank

| Status | Datum | Entscheider |
|---|---|---|
| Akzeptiert | 2026-07-16 | Lead Architecture |

## Kontext

OpenCRM benoetigt eine relationale Datenbank als einziges persistentes Speichersystem der Phase 1. Der Auftraggeber hat PostgreSQL als Backend-Datenbank verbindlich vorgegeben. Unabhaengig von dieser Vorgabe muss die Datenbank drei architekturpraegende Anforderungen tragen:

1. **Multi-Tenancy**: Mandantentrennung erfolgt im Modell Shared Database / Shared Schema; die Isolation muss auf Datenbankebene durchsetzbar sein, nicht nur im Anwendungscode (siehe [ADR-002](ADR-002-multi-tenancy-shared-schema-rls.md) und [04-multi-tenancy.md](../04-multi-tenancy.md)).
2. **Flexible Felder**: Mandanten definieren eigene Custom Fields (`custom_field_definitions`); die Werte liegen in einer `jsonb`-Spalte `custom` der jeweiligen Entitaet und muessen filterbar und indexierbar sein.
3. **Reporting**: Dashboard-KPIs werden ueber eine materialisierte Sicht `mv_sales_kpis_daily` mit `REFRESH MATERIALIZED VIEW CONCURRENTLY` vorberechnet (siehe [09-dashboard-und-reporting.md](../09-dashboard-und-reporting.md)).

Zusaetzlich verarbeitet das System Geldbetraege (`numeric`), Zeitstempel durchgaengig in UTC (`timestamptz`) und Job-Zustaende fuer Spring Batch. Das Team hat langjaehrige PostgreSQL-Erfahrung; die Betriebsumgebung (Docker Compose in Entwicklung, Kubernetes in Produktion) stellt keine Einschraenkung dar.

## Entscheidung

Wir verwenden **PostgreSQL 16** als einzige Datenbank fuer alle Module des modularen Monolithen: fachliche Daten, Spring-Batch-Metadaten sowie die Job-Tabellen `import_jobs`/`export_jobs`. Schema-Migrationen laufen ueber Flyway mit der dedizierten Rolle `opencrm_migrator`; die Anwendung verbindet sich als `opencrm_app` ohne `BYPASSRLS`.

Ausschlaggebend sind neben der Kundenvorgabe:

- **Row-Level Security (RLS)**: native, deklarative Mandantenisolation via Policies auf `tenant_id`, inklusive `FORCE ROW LEVEL SECURITY`. Kein anderes betrachtetes System bietet dies in vergleichbarer Reife.
- **JSONB**: binaer gespeicherte, mit GIN-Indexen abfragbare JSON-Spalten fuer `custom`, `criteria`, `mapping`, `filter` und `diff` – ohne zweites Speichersystem.
- **Materialisierte Sichten** mit `CONCURRENTLY`-Refresh fuer das KPI-Dashboard ohne Lese-Blockade.
- **Reife und Oekosystem**: exzellente Unterstuetzung durch Hibernate, Flyway, Spring Batch und Testcontainers; `gen_random_uuid()` fuer UUID-Primaerschluessel ist im Kern enthalten.

## Konsequenzen

### Positiv

- Ein einziges Speichersystem fuer Fachdaten, Jobs und Batch-Metadaten reduziert Betriebs- und Backup-Aufwand (eine Backup-/Restore-Strategie, ein HA-Konzept).
- RLS erlaubt Defense-in-Depth: selbst fehlerhafter Anwendungscode kann keine fremden Mandantendaten lesen.
- JSONB deckt Custom Fields, Regel-Kriterien und Import-Mappings ab, ohne dass eine Dokumentendatenbank noetig wird.
- Transaktionale Konsistenz ueber alle Module hinweg (z. B. Lead-Konvertierung schreibt `leads`, `accounts`, `contacts`, `opportunities` in einer Transaktion).
- `SELECT FOR UPDATE` traegt den Round-Robin-Zeiger der Lead-Zuweisung ohne zusaetzliche Locking-Infrastruktur.

### Negativ

- Vendor-Coupling an PostgreSQL-Spezifika (RLS, JSONB-Operatoren, `SET LOCAL`, materialisierte Sichten); ein spaeterer Datenbankwechsel waere teuer. Bewusst akzeptiert, da die Vorgabe fix ist.
- RLS-Policies verursachen pro Query einen Planner-Overhead; Indexdesign muss `tenant_id` konsequent als fuehrende Spalte beruecksichtigen.
- `REFRESH MATERIALIZED VIEW CONCURRENTLY` benoetigt einen UNIQUE Index auf der Sicht und erzeugt Last, die im Kapazitaetsplan zu beruecksichtigen ist.
- Kein eingebautes Sharding; horizontale Skalierung ueber eine Instanz hinaus erfordert spaeter Read Replicas oder Partitionierung.

## Betrachtete Alternativen

### MySQL / MariaDB

Verbreitete relationale Datenbanken mit gutem Spring-Support. Abgelehnt, weil beide keine Row-Level Security bieten – die Mandantenisolation muesste vollstaendig im Anwendungscode oder ueber Views nachgebaut werden, was das zentrale Sicherheitsargument von ADR-002 aushebelt. Die JSON-Unterstuetzung ist funktional schwaecher (kein JSONB-Aequivalent mit vergleichbarer Index-Tiefe), und materialisierte Sichten fehlen nativ. Zudem widerspricht die Wahl der verbindlichen Kundenvorgabe.

### Dokumentenorientierte Datenbank (z. B. MongoDB)

Attraktiv fuer die flexiblen Custom Fields. Abgelehnt, weil das CRM-Domaenenmodell hochgradig relational ist (Opportunities mit Positionen, Preislisten, Zuweisungshistorie, Pipelines) und Joins, Fremdschluessel-Integritaet sowie mandantenweite Aggregationen fuer das Dashboard in SQL deutlich einfacher und verlaesslicher sind. Multi-Dokument-Transaktionen und Reporting-Queries sind moeglich, aber teurer und fehleranfaelliger als in PostgreSQL; JSONB deckt den dokumentenartigen Anteil bereits ab. Ausserdem Verstoss gegen die Kundenvorgabe.

### Polyglot-Ansatz (PostgreSQL plus zweite Spezial-DB)

Etwa PostgreSQL fuer Fachdaten plus eine Suchmaschine/Dokumenten-DB fuer Custom Fields und Reporting. Abgelehnt fuer Phase 1, weil zwei Speichersysteme Synchronisation, doppelte Backups und zusaetzliche Betriebs-Skills erfordern, ohne dass eine konkrete Anforderung dies rechtfertigt. Die Baseline sieht bewusst minimale Infrastruktur vor (kein Redis, kein Broker); dieselbe Logik gilt hier.

## Offene Punkte

Alle offenen Punkte sind entschieden oder terminiert (Stand 2026-07-16) — Details im [Entscheidungsprotokoll](../13-entscheidungen.md).

1. Partitionierungsstrategie → **E-55**: zeitbasierte Range-Partitionierung (monatlich) fuer `activities` und `audit_log`; Aktivierung erst ab Schwellwert E-22, keine Vorab-DDL in M1.
2. Connection-Pool-Groesse und `work_mem` → **E-56**: Lasttest in M2; bis dahin HikariCP-Default mit max. 20 Connections je Instanz.
