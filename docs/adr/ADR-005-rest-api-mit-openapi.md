# ADR-005: REST-API mit OpenAPI 3.1

| Status | Datum | Entscheider |
|---|---|---|
| Akzeptiert | 2026-07-16 | Lead Architecture |

## Kontext

Die OpenCRM-API hat zwei Konsumentengruppen: die eigene React-SPA ([ADR-007](ADR-007-frontend-react-spa.md)) und externe Integratoren – insbesondere Partner, die Leads per API anliefern (`leads.source = API`) oder Import-/Export-Prozesse automatisieren wollen. Gefordert sind ein stabiler, versionierbarer Vertrag, gute Dokumentation, einfache Anbindung ohne Spezial-Tooling und ein einheitliches Fehlerformat. Die Baseline legt REST/JSON unter `/api/v1`, OpenAPI 3.1, RFC 9457 (`application/problem+json`), Cursor-Pagination (`cursor`/`limit`, Default 50, Maximum 200) sowie Filter- und Sortier-Query-Parameter (`sort=field` / `sort=-field`) fest. Details beschreibt [10-api-design.md](../10-api-design.md).

## Entscheidung

Wir bieten eine **REST/JSON-API unter `/api/v1`** mit einer **OpenAPI-3.1-Spezifikation** als verbindlichem Vertrag an:

- Ressourcenorientierte Endpunkte je Kernentitaet (leads, accounts, contacts, products, opportunities, activities, import-jobs, export-jobs, ...).
- Einheitliche Konventionen ueber alle Endpunkte:
  - Pagination cursor-basiert mit `cursor` und `limit` (Default 50, Maximum 200),
  - Filter als Query-Parameter, Sortierung als `sort=field` bzw. `sort=-field`,
  - Zeitstempel als ISO-8601 in UTC, Betraege mit ISO-4217-Waehrungscode.
- Die OpenAPI-Spezifikation wird aus dem Code generiert (springdoc-openapi), in der CI gegen Breaking Changes geprueft und als Teil der Doku veroeffentlicht (Swagger UI fuer Entwicklung, Redoc-artige Referenz fuer Partner).
- Fehler durchgaengig als RFC 9457 Problem Details; Pagination cursor-basiert; Authentifizierung ueber Bearer-JWT (SPA) bzw. `client_credentials`-Token des Service-Clients `opencrm-api` (Maschinenzugriffe, siehe [ADR-003](ADR-003-keycloak-single-realm-organizations.md)).
- Versionierung ueber den Pfadpraefix (`/api/v1`); Breaking Changes nur mit neuer Major-Version.

## Konsequenzen

### Positiv

- **Integrationsfreundlichkeit**: Partner binden die API mit jedem HTTP-Client an – curl, Postman, Zapier-artige Tools, jede Sprache – ohne Query-Sprache oder Codegenerator lernen zu muessen; fuer Lead-Anlieferung und Export-Abholung ist das die niedrigste Eintrittshuerde.
- **Tooling**: aus der OpenAPI-Spezifikation entstehen Client-SDKs (openapi-generator), Contract-Tests, Mock-Server und die TypeScript-Typen des Frontends – eine Quelle der Wahrheit.
- **Caching und Infrastruktur**: GET-Semantik, ETags und Standard-HTTP-Statuscodes funktionieren mit jedem Proxy, Load Balancer und Browser-Cache ohne Sonderbehandlung.
- Einheitliche, maschinenlesbare Fehler (RFC 9457) vereinfachen Fehlerbehandlung in SPA und Partner-Code.
- Spring Web und Spring Security sind fuer REST der Standardpfad – minimaler Framework-Widerstand.

### Negativ

- Kein flexibles Feld-/Graph-Fetching: die SPA braucht teils mehrere Requests oder zugeschnittene Response-DTOs (z. B. Opportunity inklusive Items); Mitigation: gezielte Compound-Endpunkte und `include`-Parameter dort, wo Messungen es rechtfertigen.
- Over-/Underfetching ist prinzipbedingt moeglich; Response-Groessen muessen im API-Design beobachtet werden.
- Versionierung ueber Pfad bedeutet bei Breaking Changes doppelte Pflege waehrend der Deprecation-Phase.
- Die Spezifikation muss aktiv gepflegt und in der CI erzwungen werden, sonst driftet der Vertrag vom Verhalten ab.

## Betrachtete Alternativen

### GraphQL

Ein Graph-Endpoint mit flexiblen Queries wuerde der SPA passgenaues Fetching erlauben. Abgelehnt, weil die zweite Zielgruppe – Integrationspartner fuer Import/Export und Lead-Anlieferung – mit REST deutlich schneller produktiv ist und GraphQL dort Mehraufwand erzeugt (Query-Sprache, Client-Bibliotheken, kein natives HTTP-Caching, N+1- und Komplexitaets-/Rate-Limiting-Probleme serverseitig). Autorisierung je Feld waere im RLS-/Rollenmodell schwerer nachvollziehbar als je Endpunkt. Der SPA-Bedarf laesst sich mit zugeschnittenen REST-Responses abdecken.

### gRPC

Effizientes binaeres Protokoll mit starken Vertraegen (Protobuf), sinnvoll fuer interne Service-zu-Service-Kommunikation mit hohem Durchsatz. Abgelehnt, weil unsere Konsumenten Browser (SPA benoetigt gRPC-Web plus Proxy) und heterogene Partner-Systeme sind, fuer die HTTP/JSON der kleinste gemeinsame Nenner ist; Debugging und Ad-hoc-Nutzung (curl, Browser-Devtools) sind mit gRPC unnoetig schwer. In einem modularen Monolithen ([ADR-004](ADR-004-modularer-monolith-spring-boot.md)) existiert zudem kein interner Netzwerkverkehr, der gRPC rechtfertigen wuerde.

### REST ohne formale Spezifikation

Schneller Start ohne OpenAPI-Pflege. Abgelehnt, weil ohne maschinenlesbaren Vertrag weder Frontend-Typgenerierung noch Partner-SDKs noch Breaking-Change-Checks in der CI moeglich sind; die Dokumentation wuerde manuell gepflegt und veralten. Die Kosten von springdoc-openapi im Build sind minimal gegenueber diesem Nutzen.

## Offene Punkte

Alle offenen Punkte sind entschieden oder terminiert (Stand 2026-07-16) — Details im [Entscheidungsprotokoll](../13-entscheidungen.md).

1. Rate-Limiting Service-Client → **E-39**: 600 Requests/min je Client; Durchsetzung im Backend.
2. `include`-Parameter vs. Compound-Endpunkte → **E-65**: keine `include`-Parameter zum v1-Start; Compound-Endpunkte erst nach SPA-Messungen in M2.
3. Webhooks → **E-66**: ja, als Ausbaustufe nach M3 mit HMAC-Signatur.
4. Deprecation-Policy → **E-67**: 6 Monate Ankuendigungsfrist, `Deprecation`-/`Sunset`-Header; verbindlich dokumentiert vor dem ersten externen Partner.
