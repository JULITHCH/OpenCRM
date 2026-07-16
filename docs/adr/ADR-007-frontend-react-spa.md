# ADR-007: Frontend als React-SPA

| Status | Datum | Entscheider |
|---|---|---|
| Akzeptiert | 2026-07-16 | Lead Architecture |

## Kontext

OpenCRM braucht ein Web-Frontend fuer die Kernablaeufe: Lead-Listen mit Filterung und Zuweisung, Opportunity-Bearbeitung mit Positionen, Import-Assistent mit Spalten-Mapping, und ein KPI-Dashboard mit Charts ([09-dashboard-und-reporting.md](../09-dashboard-und-reporting.md)). Die Anwendung ist eine reine **eingeloggte B2B-Anwendung**: Es gibt keine oeffentlichen Inhalte, jede Seite liegt hinter dem Keycloak-Login ([ADR-003](ADR-003-keycloak-single-realm-organizations.md)). Suchmaschinen-Sichtbarkeit (SEO) ist damit irrelevant. Das Backend stellt ausschliesslich die REST-API unter `/api/v1` bereit ([ADR-005](ADR-005-rest-api-mit-openapi.md)); die Oberflaeche muss zweisprachig sein (de/en).

## Entscheidung

Wir bauen das Frontend als **Single-Page-Application mit React 18 und TypeScript**:

- Build und Dev-Server mit **Vite** (Dev-Port 5173), Auslieferung als statische Assets aus einem eigenen Docker-Image.
- **TanStack Query** fuer Server-State (Caching, Invalidierung, Pagination ueber `cursor`/`limit`); Client-State bleibt minimal.
- **MUI (Material UI)** als UI-Komponentenbibliothek (E-06), **Recharts** fuer die Dashboard-Charts, **react-i18next** fuer de/en.
- Anzeige-Konventionen gemaess Baseline: Zeitstempel kommen als UTC von der API und werden in der Nutzer-Zeitzone dargestellt; Betraege mit ISO-4217-Waehrung und Default-Waehrung des Tenants.
- Rollenbasierte Sichtbarkeit (z. B. Dashboard-Einschraenkungen fuer `sales-rep`) steuert das Backend; die SPA blendet lediglich Navigation und Aktionen anhand der Rollen aus dem Token aus.
- Login ueber Keycloak mit Authorization Code Flow plus PKCE (public Client `opencrm-web`); die SPA haelt Tokens im Speicher und spricht ausschliesslich die REST-API.
- API-Typen werden aus der OpenAPI-3.1-Spezifikation generiert – der API-Vertrag ist die einzige Kopplung zwischen Frontend und Backend.

## Konsequenzen

### Positiv

- **Team-Verfuegbarkeit und Ecosystem**: React/TypeScript-Erfahrung ist im Team vorhanden und am Arbeitsmarkt am breitesten verfuegbar; fuer jede benoetigte Bausteinklasse (Tabellen, Formulare, Charts, i18n, Query-Caching) existieren gepflegte, verbreitete Bibliotheken.
- Klare Trennung: Frontend ist ein statisches Artefakt ohne Server-Laufzeit – triviales Hosting (Nginx-Container), unabhaengige Deploy-Zyklen von Backend und Frontend, einfaches Rollback.
- TanStack Query passt exakt zum API-Design (Cursor-Pagination, Cache-Invalidierung nach Mutationen, Hintergrund-Refetch fuer das Dashboard).
- SPA-Interaktionsmodell traegt die datenintensiven Screens (Inline-Editing in Listen, mehrstufiger Import-Assistent, Live-Filter im Dashboard) ohne Full-Page-Reloads.
- Typgenerierung aus OpenAPI verhindert stille Vertragsbrueche zwischen SPA und API.
- Vite liefert schnelle Dev-Feedback-Zyklen (HMR) und einen schlanken Produktions-Build ohne eigene Webpack-Pflege.

### Negativ

- Initiale Bundle-Groesse und Time-to-First-Render sind schlechter als bei SSR; fuer eine eingeloggte Fachanwendung mit langen Sessions akzeptabel, Mitigation ueber Code-Splitting je Route.
- SPA-Sicherheitsthemen (Token-Handling im Browser, XSS-Disziplin, CSP) liegen in eigener Verantwortung und muessen im Review-Prozess verankert werden.
- React gibt wenig Struktur vor: Ordner-/State-Konventionen muessen teamintern definiert und durchgehalten werden (Frontend-Leitfaden noetig).
- Kein serverseitiges Rendering bedeutet: ohne JavaScript keine Anwendung (fuer die Zielgruppe irrelevant).

## Betrachtete Alternativen

### Angular

Vollstaendiges Framework mit starken Konventionen, Dependency Injection und eingebauter i18n – gut fuer grosse Enterprise-Teams. Abgelehnt, weil im Team keine nennenswerte Angular-Erfahrung vorhanden ist und die Lernkurve (Modole/Standalone-Umbrueche, RxJS-Pflicht) den Start verzoegern wuerde, waehrend React-Kenntnisse sofort produktiv sind. Das Ecosystem fuer unsere konkreten Bausteine (TanStack Query, Recharts) ist React-seitig breiter; der Konventionsvorteil von Angular wiegt das bei einem 2–3-Personen-Frontend nicht auf.

### Serverseitiges Rendering / Meta-Framework (Next.js, Remix)

SSR/SSG verbessert SEO und ersten Seitenaufbau und boete Server-Funktionen. Abgelehnt, weil beide Vorteile hier nicht ziehen: Alle Inhalte liegen hinter dem Login, SEO ist irrelevant, und personalisierte CRM-Daten sind nicht sinnvoll vorzurendern. SSR braechte eine Node-Laufzeit als zusaetzliche Betriebs-Komponente (Deployment, Monitoring, Skalierung) und vermischt die klare Trennung "statisches Frontend + REST-API" – Komplexitaet ohne Gegenwert fuer eine B2B-Fachanwendung.

### Serverseitige Templates im Backend (Thymeleaf/HTMX)

Rendering direkt aus Spring Boot, kein separates Frontend-Artefakt. Abgelehnt, weil die geforderte Interaktivitaet (Dashboard mit Filtern und Charts, Import-Assistent mit clientseitigem Mapping, Inline-Bearbeitung) mit Template-Rendering nur mit erheblichem JavaScript-Eigenanteil erreichbar waere und die API-First-Strategie ([ADR-005](ADR-005-rest-api-mit-openapi.md)) unterlaufen wuerde – die SPA erzwingt, dass jede Funktion ueber die oeffentliche API verfuegbar ist und damit auch fuer Integratoren bereitsteht.

## Offene Punkte

Alle offenen Punkte sind entschieden oder terminiert (Stand 2026-07-16) — Details im [Entscheidungsprotokoll](../13-entscheidungen.md).

1. UI-Komponentenbibliothek → **E-06**: MUI (Material UI).
2. Token-Ablage → **E-71**: Access-Token nur in-memory, Silent-Refresh, Refresh-Token-Rotation aktiviert.
3. E2E-Test-Werkzeug → **E-72**: Playwright; Kern-Flows: Login, Lead-Anlage mit Zuweisung, Import-Dry-Run, Dashboard.
4. Barrierefreiheits-Zielniveau → **E-07**: pragmatisch ab Phase 1; formales WCAG-2.1-AA-Audit erst bei vertraglicher Anforderung.
