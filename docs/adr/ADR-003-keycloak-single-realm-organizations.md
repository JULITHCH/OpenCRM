# ADR-003: Keycloak – Ein Realm mit Organizations

| Status | Datum | Entscheider |
|---|---|---|
| Akzeptiert | 2026-07-16 | Lead Architecture |

## Kontext

Der Auftraggeber gibt Keycloak als Authentifizierungsloesung vor. Offen war der Zuschnitt: Keycloak kann Mandanten entweder ueber je einen eigenen Realm oder – seit Keycloak 25 als Preview, seit Keycloak 26 voll unterstuetzt – ueber **Organizations** innerhalb eines Realms abbilden. Die Wahl bestimmt Betriebsaufwand, Login-Erlebnis, Token-Inhalte und die Provisionierung neuer Mandanten. Randbedingungen:

- Das Backend erwartet in jedem Access Token die Claims `tenant_id` (UUID) und `org_slug`, da daraus die RLS-Session-Variable gesetzt wird (siehe [ADR-002](ADR-002-multi-tenancy-shared-schema-rls.md)).
- Geplant sind 200+ Mandanten mit weitgehend identischen Sicherheitsanforderungen; einzelne Kunden werden mittelfristig SSO gegen ihren eigenen IdP verlangen.
- Onboarding eines Mandanten soll automatisierbar sein (API-gesteuert, ohne manuelle Keycloak-Administration).

Details der Umsetzung beschreibt [05-authentifizierung-keycloak.md](../05-authentifizierung-keycloak.md).

## Entscheidung

Wir betreiben **Keycloak 26 mit genau einem Realm `opencrm`** fuer alle Mandanten und bilden jeden Tenant als **Keycloak Organization** ab:

- Eine Organization je Tenant; Organization-Attribute halten `tenant_id` (UUID) und `org_slug`.
- Ein Protocol Mapper schreibt `tenant_id` und `org_slug` in jedes Access Token.
- Ein Frontend-Client `opencrm-web` (public, Authorization Code Flow mit PKCE) und ein Service-Client `opencrm-api` (confidential, `client_credentials`) – jeweils einmal fuer die ganze Plattform.
- Realm-Rollen `platform-admin`, `tenant-admin`, `sales-manager`, `sales-rep`, `read-only` gelten realm-weit; die Mandantenzuordnung kommt aus der Organization-Mitgliedschaft, nicht aus der Rolle.
- User-Provisionierung in der App-DB erfolgt Just-in-Time beim ersten Login (`users`-Tabelle als Spiegel, Rollen-Sync bei jedem Login).
- IdP-Foederation (z. B. Kunden-Azure-AD) wird bei Bedarf **je Organization** angebunden; Organizations unterstuetzen Domain-basiertes IdP-Routing.

## Konsequenzen

### Positiv

- Ein Realm bedeutet einmalige Pflege von Clients, Rollen, Token-Lifetimes, Passwort-Policies, E-Mail- und Theme-Konfiguration – statt n-facher Kopien mit Drift-Risiko.
- Tenant-Onboarding ist ein API-Aufruf (Organization anlegen, Attribute setzen) statt Realm-Provisionierung mit Client- und Mapper-Setup.
- Einheitliches Login-Erlebnis: eine Login-URL fuer alle Mandanten, die SPA benoetigt keine tenant-spezifische Keycloak-Konfiguration; Organization-Auswahl bzw. E-Mail-Domain-Routing uebernimmt Keycloak.
- Backend und Frontend validieren gegen genau einen Issuer – kein dynamisches Multi-Issuer-Handling im OAuth2 Resource Server.
- Kunden-IdPs lassen sich pro Organization foederieren, ohne dass dafuer eigene Realms noetig werden.

### Negativ

- **Schwaechere Konfigurations-Isolation je Mandant**: Passwort-Policy, MFA-Pflicht, Token-Lifetimes und Login-Theme gelten realm-weit; tenant-individuelle Abweichungen sind nur eingeschraenkt (z. B. ueber IdP-seitige Policies) moeglich. Bewusst akzeptiert; harte Sonderwuensche einzelner Grosskunden waeren ein Argument fuer einen dedizierten Realm als Ausnahme.
- Ein Fehler in der Realm-Konfiguration betrifft alle Mandanten gleichzeitig (Blast Radius); Gegenmassnahme: Konfiguration als Code (Realm-Export im Repo) und Staging-Realm.
- Der Protocol Mapper und die Organization-Attribute sind sicherheitskritisch: ein falsch gesetztes `tenant_id`-Attribut ordnet Nutzer dem falschen Mandanten zu; Gegenmassnahme: Provisionierung ausschliesslich ueber einen geprueften Admin-API-Pfad plus Konsistenz-Check gegen die `tenants`-Tabelle beim JIT-Provisioning.
- Realm-weite Rollen erlauben keine Mandanten-spezifischen Rollendefinitionen; feinere Berechtigungen muessen in der App abgebildet werden.

## Betrachtete Alternativen

### Realm pro Tenant

Klassisches Keycloak-Multi-Tenancy-Muster mit maximaler Konfigurations-Isolation. Abgelehnt, weil bei 200+ Tenants jede Aenderung an Clients, Mappern oder Policies ueber alle Realms ausgerollt werden muss und Keycloak bei sehr vielen Realms messbar an Verwaltungs- und Startup-Performance verliert. Das Login-Erlebnis zerfaellt in tenant-spezifische URLs, und Backend wie SPA muessten Issuer dynamisch aufloesen. Der Isolationsgewinn wird fuer unsere gleichartig konfigurierten Mandanten nicht benoetigt.

### Eigenbau-Auth (Spring Security + eigene User-Verwaltung)

Eigene Nutzer-, Passwort- und Session-Verwaltung direkt in der Anwendung. Abgelehnt: sicherheitskritische Funktionalitaet (Passwort-Hashing, MFA, Brute-Force-Schutz, Session-Handling, SSO, IdP-Foederation) selbst zu bauen ist teuer, fehleranfaellig und auditierungspflichtig – Keycloak liefert all das gehaertet und gepflegt. Zudem widerspricht Eigenbau der verbindlichen Kundenvorgabe Keycloak.

### Gemeinsamer Realm ohne Organizations (Gruppen/Attribute als Tenant-Marker)

Vor Keycloak Organizations uebliches Muster: Tenant-Zuordnung ueber Gruppen oder User-Attribute. Abgelehnt, weil Organizations genau dieses Muster als Erstklass-Feature mit Mitglieder-Verwaltung, Domain-Routing und Organization-scoped IdP-Foederation ersetzen; ein Gruppen-Eigenbau muesste Einladungs-Flows und IdP-Zuordnung manuell nachbilden und wuerde bei einem spaeteren Wechsel auf Organizations Migrationsaufwand erzeugen.

## Offene Punkte

1. MFA-Strategie (realm-weit verpflichtend vs. optional je Nutzer) ist noch nicht entschieden.
2. Ausnahme-Prozess fuer Grosskunden mit hartem Bedarf an eigenem Realm (Vertragsfrage) ist offen.
3. Werkzeug fuer Keycloak-Konfiguration-as-Code (Realm-JSON-Import vs. Terraform-Provider vs. keycloak-config-cli) ist noch auszuwaehlen.
