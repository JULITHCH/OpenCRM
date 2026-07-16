# ADR-002: Multi-Tenancy – Shared Schema mit Row-Level Security

| Status | Datum | Entscheider |
|---|---|---|
| Akzeptiert | 2026-07-16 | Lead Architecture |

## Kontext

OpenCRM ist mandantenfaehig: mehrere Kunden-Organisationen nutzen dieselbe Plattform, ihre Daten muessen strikt getrennt sein. Die Planung geht mittelfristig von 200+ Tenants aus, ueberwiegend kleine bis mittlere Vertriebsorganisationen mit stark unterschiedlicher Datenmenge. Grundsaetzlich existieren drei Isolationsmodelle: eigene Datenbank je Tenant, eigenes Schema je Tenant in einer Datenbank, oder gemeinsames Schema mit Tenant-Diskriminator-Spalte. Die Entscheidung praegt Betrieb, Migrationen, Kosten, Onboarding-Geschwindigkeit und das Sicherheitsmodell. Details der Umsetzung beschreibt [04-multi-tenancy.md](../04-multi-tenancy.md); die Datenbankwahl begruendet [ADR-001](ADR-001-postgresql-als-datenbank.md).

## Entscheidung

Wir setzen **Shared Database / Shared Schema mit Row-Level Security (RLS)** um:

- Jede mandantenbezogene Tabelle traegt eine Spalte `tenant_id UUID NOT NULL`; die Tabelle `tenants` ist plattformglobal.
- Auf allen mandantenbezogenen Tabellen sind RLS-Policies aktiv, die `tenant_id = current_setting('app.current_tenant', true)::uuid` vergleichen; zusaetzlich `ALTER TABLE ... FORCE ROW LEVEL SECURITY`, damit die Policies auch fuer den Tabellen-Owner gelten.
- Die Anwendung setzt pro Transaktion `SET LOCAL app.current_tenant = '<uuid>'`; der Wert stammt ausschliesslich aus dem validierten JWT-Claim `tenant_id` (siehe [05-authentifizierung-keycloak.md](../05-authentifizierung-keycloak.md)).
- Die App-DB-Rolle `opencrm_app` hat kein `BYPASSRLS`; Flyway-Migrationen laufen ueber die separate Rolle `opencrm_migrator`.
- Mandantenuebergreifende Betreiber-Funktionen (Rolle `platform-admin`) laufen ueber explizit gekennzeichnete Pfade, nie ueber ein Abschalten der Policies zur Laufzeit in regulaeren Endpunkten.

## Konsequenzen

### Positiv

- **Ein** Schema, **eine** Flyway-Migrationskette: ein Deployment migriert alle Tenants atomar; kein Drift zwischen Tenant-Schemata.
- Betriebskosten und -aufwand wachsen nicht linear mit der Tenant-Zahl; 200+ Tenants auf einer PostgreSQL-Instanz sind realistisch (Connection-Pooling, Backups, Monitoring nur einmal).
- Tenant-Onboarding ist ein einfacher `INSERT` in `tenants` plus Keycloak-Organization – Sekunden statt Provisionierungs-Pipeline.
- RLS wirkt als Sicherheitsnetz unterhalb der Anwendung: ein vergessener `WHERE tenant_id = ?`-Filter fuehrt zu leeren Ergebnissen, nicht zu Datenabfluss.
- Mandantenuebergreifende Plattform-Auswertungen (Betreiber) bleiben mit einer Query moeglich.

### Negativ

- **Risiko Fehlkonfiguration**: eine Tabelle ohne Policy, eine Verbindung ohne `SET LOCAL` oder eine Rolle mit `BYPASSRLS` kompromittiert die Isolation. Folgende Gegenmassnahmen sind verbindlich:
  - Automatisierter Testcontainers-Test, der fuer jede Tabelle mit Spalte `tenant_id` prueft, dass RLS aktiviert und `FORCE ROW LEVEL SECURITY` gesetzt ist; fehlende Policies lassen den Build fehlschlagen.
  - Integrationstests mit zwei Test-Tenants, die Cross-Tenant-Lese- und Schreibversuche gegen jeden Repository-Pfad ausfuehren und leere Ergebnisse bzw. Fehler erwarten.
  - CI-Check, dass jede neue Flyway-Migration mit `CREATE TABLE` und `tenant_id` auch die zugehoerigen Policies anlegt.
  - Review-Pflicht (Vier-Augen-Prinzip) fuer jede Aenderung an DB-Rollen und Grants; regelmaessiger Audit-Query auf Rollen mit `BYPASSRLS` oder `SUPERUSER`.
- Kein "Noisy Neighbor"-Schutz auf DB-Ebene: ein grosser Tenant kann die Instanz belasten; Mitigation ueber Statement-Timeouts, Pagination-Limits und Monitoring je Tenant.
- Restore eines einzelnen Tenants ist aufwendiger (selektiver Export statt Datenbank-Restore).
- Planner-Overhead durch Policies; alle Indexe muessen `tenant_id` als fuehrende Spalte fuehren.
- Compliance-Grenzen: Kunden mit harter Anforderung an physische Trennung koennen in diesem Modell nicht bedient werden (bewusst akzeptiert; siehe Migrationspfad in [12-roadmap.md](../12-roadmap.md)).

## Betrachtete Alternativen

### Schema pro Tenant

Jeder Tenant erhaelt ein eigenes PostgreSQL-Schema mit identischen Tabellen. Abgelehnt, weil bei 200+ Tenants jede Flyway-Migration 200+-fach laufen muss – Deployments werden langsam, teilweise fehlgeschlagene Migrationen erzeugen Schema-Drift und einen inkonsistenten Flotten-Zustand. Zusaetzlich degradieren PostgreSQL-Katalog und Connection-Pools bei zehntausenden Tabellen, und Cross-Tenant-Auswertungen des Betreibers erfordern generiertes SQL ueber alle Schemata. Der Isolationsgewinn gegenueber RLS rechtfertigt diese Betriebslast nicht.

### Datenbank pro Tenant

Jeder Tenant erhaelt eine eigene Datenbank oder Instanz. Staerkste Isolation und einfacher Einzel-Tenant-Restore, aber abgelehnt: Kosten und Betriebsaufwand (Backups, Monitoring, Verbindungen, Migrationen, Kapazitaetsplanung) skalieren linear mit der Tenant-Zahl und sind bei 200+ kleinen Tenants wirtschaftlich nicht darstellbar. Onboarding braeuchte eine Provisionierungs-Pipeline statt eines INSERTs. Das Modell bleibt als spaeterer Sonderpfad fuer einzelne Grosskunden denkbar, ist aber keine Basis fuer die Plattform.

### Nur applikative Filterung (Discriminator ohne RLS)

Shared Schema, Isolation ausschliesslich ueber `WHERE tenant_id = ?` im Anwendungscode (z. B. Hibernate-Filter). Abgelehnt, weil ein einziger vergessener Filter, eine native Query oder ein fehlerhafter Batch-Job unmittelbar Cross-Tenant-Datenabfluss bedeutet. RLS liefert dieselbe Datenhaltung mit einem erzwungenen Sicherheitsnetz auf DB-Ebene bei geringem Mehraufwand; auf dieses Netz zu verzichten ist kein vertretbarer Trade-off.

## Offene Punkte

1. Verfahren fuer Einzel-Tenant-Export/-Restore (Offboarding, Datenpannen-Fall) muss als Runbook ausgearbeitet werden.
2. Grenzwerte, ab denen ein Grosskunde auf eine dedizierte Instanz umzieht, sind nicht definiert.
3. Ob `pg_stat_statements`-Auswertung je Tenant (Label ueber `application_name` o. ae.) umgesetzt wird, ist offen.
