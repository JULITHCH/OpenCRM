# ADR-004: Modularer Monolith mit Spring Boot und Java 21

| Status | Datum | Entscheider |
|---|---|---|
| Akzeptiert | 2026-07-16 | Lead Architecture |

## Kontext

Fuer OpenCRM sind Architekturstil und Technologie-Stack des Backends festzulegen. Rahmenbedingungen: ein kleines Umsetzungsteam (geplant 4–6 Entwickler), ein fachlich zusammenhaengendes Domaenenmodell (Leads, Accounts, Opportunities, Produkte, Aktivitaeten teilen sich Transaktionen und Referenzen), eine einzige PostgreSQL-Datenbank ([ADR-001](ADR-001-postgresql-als-datenbank.md)) und der bewusste Verzicht auf Message-Broker und Redis in Phase 1. Gleichzeitig soll die Architektur eine spaetere Herausloesung einzelner Teile (z. B. Import/Export oder Reporting) nicht verbauen. Der Gesamtschnitt ist in [02-systemarchitektur.md](../02-systemarchitektur.md) beschrieben.

## Entscheidung

Wir bauen das Backend als **modularen Monolithen** mit **Java 21 und Spring Boot 3.3**, Build mit Maven:

- Modul-Schnitt nach Spring-Modulith-Konventionen: `tenant`, `identity`, `crmcore` (accounts/contacts), `lead`, `sales` (products/opportunities/pipelines), `activity`, `importexport`, `reporting`, `shared`.
- Module kommunizieren ueber oeffentliche APIs (Java-Interfaces) und applikationsinterne Domain-Events; direkte Zugriffe auf interne Klassen fremder Module verhindert Spring Modulith per Verifikationstest in der CI.
- Ein Deployment-Artefakt (ein Docker-Image), eine Datenbank, eine Flyway-Migrationskette.
- Stack-Bausteine:
  - Spring Web fuer die REST-API ([ADR-005](ADR-005-rest-api-mit-openapi.md)),
  - Spring Security OAuth2 Resource Server fuer die JWT-Validierung gegen Keycloak ([ADR-003](ADR-003-keycloak-single-realm-organizations.md)),
  - Spring Data JPA/Hibernate fuer die Persistenz, Flyway fuer Migrationen,
  - Spring Batch fuer Import/Export und geplante Jobs ([ADR-006](ADR-006-import-export-mit-spring-batch.md)),
  - Micrometer/Prometheus und OpenTelemetry fuer Observability.

## Konsequenzen

### Positiv

- **Deployment-Einfachheit**: ein Artefakt, ein Rollout, ein Rollback; kein verteiltes Versions-Matching, keine Service-Discovery, keine netzwerkbedingten Teilausfaelle zwischen Modulen.
- Fachliche Transaktionen (Lead-Konvertierung, Opportunity mit Positionen, Zuweisung mit Historie) laufen als lokale ACID-Transaktionen – keine Sagas, keine Eventual Consistency.
- Passend zur Teamgroesse: 4–6 Entwickler pflegen eine Codebasis mit einem Build statt einer Service-Flotte mit je eigener Pipeline.
- Spring Modulith erzwingt Modulgrenzen testbar; die Architektur bleibt kein "Big Ball of Mud".
- **Spaetere Zerlegung entlang der Modulgrenzen** bleibt moeglich: Module mit eigener API und Event-Kommunikation (Kandidaten: `importexport`, `reporting`) lassen sich extrahieren, wenn Last oder Teamschnitt es erfordern.
- Java 21 (Virtual Threads, Records, Pattern Matching) und Spring Boot 3.3 sind LTS-nah, personell gut besetzbar und langfristig gewartet.

### Negativ

- Skalierung nur als Ganzes: das Deployment skaliert horizontal ueber Kubernetes-Replicas, aber einzelne Module lassen sich nicht unabhaengig dimensionieren.
- Ein Speicher-/CPU-Problem eines Moduls (z. B. grosser Import) betrifft die ganze JVM; Mitigation: Spring-Batch-Chunking, Statement-Timeouts, ggf. spaeter dedizierte Worker-Deployments desselben Images.
- Modulgrenzen sind Disziplin- plus Toolingfrage; ohne die Modulith-Verifikation in der CI erodieren sie.
- Ein Release-Zug fuer alle Module: Teams koennen nicht unabhaengig deployen (bei der Teamgroesse akzeptabel).

## Betrachtete Alternativen

### Microservices

Ein Service je Fachdomaene mit eigener Datenhaltung und asynchroner Kommunikation. Abgelehnt fuer Phase 1: Bei 4–6 Entwicklern erzeugt der Betrieb von 6–9 Services (Pipelines, Deployments, Observability, Vertragsmanagement zwischen Services) mehr Aufwand als Nutzen, und das stark verwobene CRM-Domaenenmodell wuerde verteilte Transaktionen oder Eventual Consistency an Stellen erzwingen, die fachlich strikte Konsistenz brauchen (Zuweisung, Konvertierung, Betragsdenormalisierung). Die Baseline ohne Message-Broker macht sinnvolle Microservice-Kommunikation zusaetzlich unpraktisch. Der modulare Monolith haelt den Migrationspfad offen, ohne die Kosten vorwegzunehmen.

### Node.js / NestJS

TypeScript ueber Frontend und Backend hinweg, schneller Start, grosses Oekosystem. Abgelehnt, weil die tragenden Bausteine der Baseline im JVM-Oekosystem deutlich reifer sind: Es gibt kein NestJS-Aequivalent zu Spring Batch (Restart, Chunking, Job-Repository) und zu Spring Modulith; JPA/Hibernate plus Flyway sind fuer das RLS-Setup mit `SET LOCAL` erprobt. Zudem liegt die Backend-Erfahrung des Teams ueberwiegend in Java, und CPU-intensive Batch-Verarbeitung passt schlechter zum Single-Threaded-Event-Loop-Modell.

### .NET (ASP.NET Core / C#)

Technisch ebenbuertiger, moderner Stack mit gutem PostgreSQL-Support (Npgsql, EF Core). Abgelehnt primaer aus Team- und Oekosystemgruenden: keine vorhandene .NET-Erfahrung im Team, waehrend Spring-Know-how vorhanden ist; fuer Spring Batch und Spring Modulith existieren keine gleichwertigen Erstklass-Pendants (Hangfire u. ae. decken Scheduling, aber nicht Batch-Restart-Semantik ab). Ein Stack-Wechsel ohne fachlichen Mehrwert erhoeht nur das Projektrisiko.

## Offene Punkte

1. Ob `importexport` als zweites Deployment desselben Images (Worker-Profil) betrieben wird, sobald Importlast stoert, ist noch nicht entschieden.
2. Einsatz von Virtual Threads fuer Request-Verarbeitung (Tomcat-Konfiguration) muss unter RLS-/Pool-Verhalten getestet werden.
3. Verbindlichkeit der Spring-Modulith-Verifikation (Build-Fehler vs. Warnung) in der CI ist festzulegen.
