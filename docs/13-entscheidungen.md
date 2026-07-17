# Entscheidungsprotokoll

| | |
|---|---|
| Status | Beschlossen |
| Stand | 2026-07-16 |
| Verantwortlich | Lead Architecture / Product Owner |

## Zweck

Dieses Dokument löst die in den Kapiteln 01–12 und den ADRs gesammelten „Offene Punkte" auf. Jede Entscheidung hat eine ID (E-xx), auf die die Kapitel verweisen. Entscheidungen der Kategorie **PO** hat der Product Owner am 2026-07-16 getroffen, Kategorie **ARCH** hat die Architektur mit begründetem Default festgelegt, Kategorie **EXTERN** sind bewusst terminierte, nicht blockierende Aufgaben außerhalb des Entwicklungsteams.

## 1. Produktentscheidungen (PO, 2026-07-16)

| ID | Thema | Entscheidung |
|----|-------|--------------|
| E-01 | Währungen | **Eine Währung je Mandant** (Phase 1). `products.currency` und `opportunities.currency` müssen dem Tenant-Default (`tenants.default_currency`) entsprechen; der Service-Layer erzwingt das. Die Spalten bleiben für eine spätere Multi-Currency-Ausbaustufe erhalten. KPIs summieren ohne Umrechnung in der Mandanten-Währung. |
| E-02 | DSGVO-Löschung | **Anonymisierung statt physischer Löschung**: personenbezogene Felder werden unwiderruflich überschrieben (Namen/E-Mail/Telefon → `NULL` bzw. Platzhalter), Datensätze und KPIs bleiben konsistent. Default-Fristen: Leads 12 Monate nach Disqualifikation, `audit_log` 24 Monate — je Tenant über `tenants.settings` konfigurierbar. Juristische Bestätigung läuft parallel (E-50 ff.), blockiert die Entwicklung nicht. |
| E-03 | Tarife/Quotas | **Ein Standard-Plan in Phase 1** (`tenants.plan = 'standard'`): alle Mandanten erhalten dieselben technischen Schutz-Limits (u. a. Import max. 100 000 Zeilen / 50 MB). Tarif-Differenzierung entscheidet das Produktmanagement bis Ende M3. |
| E-04 | MFA | **TOTP-Pflicht für `platform-admin` und `tenant-admin`**; für alle anderen Rollen Opt-in, je Organization aktivierbar. |
| E-05 | Support-Zugriff | **„Assume Tenant" erlaubt mit Schutzmaßnahmen**: zeitlich begrenzt (max. 4 h), expliziter Grund erforderlich, jede Aktion im `audit_log` als Support-Zugriff markiert, automatische Benachrichtigung an die tenant-admins des betroffenen Mandanten. |
| E-06 | UI-Bibliothek | **MUI (Material UI)** für das React-Frontend. |
| E-07 | Barrierefreiheit | **Pragmatisch ab Phase 1** (Tastaturbedienung, Kontraste, Labels, Fokusführung, automatisierte axe-Checks in der CI); formales WCAG-2.1-AA-Audit erst bei vertraglicher Anforderung. |
| E-08 | Opportunity-Import | **Bleibt spätere Ausbaustufe** (nach M3). Phase 1–2 importiert Leads, Accounts, Contacts, Products; das Fundament (`external_id`, E-11) wird in Phase 1 gelegt. |

## 2. Architekturentscheidungen (ARCH, 2026-07-16)

### Datenmodell

| ID | Thema | Entscheidung |
|----|-------|--------------|
| E-09 | Lead-Abschluss-Zeitpunkte | `leads` erhält `converted_at` und `disqualified_at` (`timestamptz`, NULL). Lead-Conversion-KPI und `converted_leads` nutzen diese Spalten statt der `updated_at`-Näherung. |
| E-10 | Account-Preisliste | `accounts.price_list_id` (uuid, NULL, FK → `price_lists`): genau eine optionale Preisliste je Account. n:m-Zuordnung abgelehnt (Phase-1-Komplexität). |
| E-11 | Externe Schlüssel | Alle importierbaren Entitäten (`leads`, `accounts`, `contacts`, `products`) erhalten `external_id` (text, NULL) mit partiellem UNIQUE-Index je Tenant. Beim Import ist `external_id` der bevorzugte Match-Schlüssel; die bisherigen Schlüssel (E-Mail, `name`+`postal_code`, `sku`) bleiben Fallback. |
| E-12 | Tenant-Einstellungen | `tenants.settings jsonb NOT NULL DEFAULT '{}'` für mandantenspezifische Einstellungen (u. a. `lead_claim_enabled`, SLA-Frist, Duplikat-Schwellen, Aufbewahrungsfristen). Keine eigene Tabelle `tenant_settings`. |
| E-13 | Fehlende Tabellen | `notifications` (In-App-Benachrichtigungen) und `round_robin_pointers` (Zuweisungszeiger je Team) werden ins kanonische Datenmodell (Kapitel 03) aufgenommen; sie waren bisher nur in Kapitel 06 beschrieben. |
| E-14 | Schätzbetrag | `opportunities.is_estimated boolean NOT NULL DEFAULT false`: Eine Opportunity ohne Positionen darf einen manuellen Schätzbetrag führen (`amount` gesetzt, `is_estimated = true`). Mit der ersten Position wird `amount` aus den Positionen berechnet und `is_estimated = false` gesetzt. Der Forecast zählt Schätzwerte mit. Revidiert die frühere Festlegung „`amount = 0` ohne Positionen". |
| E-15 | Account-Owner | `accounts.owner_id` bleibt nullable. Beim Import kann optional ein Default-Owner je Import-Job gesetzt werden; kein Zwang. |
| E-16 | E-Mail-Eindeutigkeit | Kein UNIQUE-Constraint auf `leads.email`/`contacts.email` (bleibt). Dubletten werden über Import-Strategie und UI-Warnungen behandelt; harte Eindeutigkeit ggf. später als Tenant-Setting. |
| E-17 | Custom Fields | `custom jsonb` bleibt in Phase 1 auf `leads` beschränkt; Erweiterung auf accounts/contacts/products ist Backlog (Priorisierung Produktmanagement). |
| E-18 | probability-Semantik | `pipeline_stages.probability` ist verbindlich ein **Prozentwert 0.00–100.00** (CHECK-Constraint); alle Forecast-Formeln rechnen `amount * probability / 100`. |
| E-19 | Primärteam | `team_members` erhält `is_primary boolean NOT NULL DEFAULT false` mit partiellem UNIQUE-Index (genau ein Primärteam je Nutzer). Die Dashboard-MV ordnet Kennzahlen dem Primärteam zu (ersetzt „kleinstes team_id"). |
| E-20 | audit_log-Aufbewahrung | 24 Monate Default, je Tenant konfigurierbar (E-12); vor Löschung Archiv-Export in den Objekt-Storage. Rechtliche Bestätigung parallel (E-02). |

### Multi-Tenancy, Auth, Lead-Management

| ID | Thema | Entscheidung |
|----|-------|--------------|
| E-21 | Offboarding-Frist | 30 Kalendertage Default bestätigt (Arbeitsstand); juristische Bestätigung parallel, Frist über `tenants.settings` anpassbar. |
| E-22 | Skalierungsschwelle | Partitionierungs-/Sharding-Review ab **> 50 Mio. Zeilen in `activities`** oder **> 500 aktiven Mandanten** (bestätigt). |
| E-23 | Produktions-Domains | Platzhalter (`auth.opencrm.example`, `app.opencrm.example`) bleiben bis zur Domain-Entscheidung (EXTERN, E-53); alle URLs sind über Konfiguration/Helm-Values gesetzt, nichts ist hart kodiert. |
| E-24 | Rollen-Downgrade | Die 5-Minuten-Access-Token-Lifetime genügt bei Rollenwechsel. Bei **Deaktivierung** eines Nutzers werden zusätzlich seine Keycloak-Sessions über die Admin-API beendet. |
| E-25 | Kunden-SSO | Identity Brokering je Organization bleibt Phase 2+; Anforderungsaufnahme beim ersten konkreten Kundenbedarf. |
| E-26 | Manager-Sichtbarkeit | Kriterium bleibt `team_members.is_lead = true`. Ein Manager kann in mehreren Teams `is_lead` sein und sieht die Vereinigungsmenge dieser Teams. Kein zusätzliches Konstrukt. |
| E-27 | Regel-Engine-Trigger | Zuweisungsregeln laufen **nur bei Lead-Anlage**; zusätzlich manueller Re-Run über `POST /leads/{id}/reapply-rules` sowie Bulk-Variante für Leads im Status `NEW`. Kein automatischer Re-Run bei Feldänderungen. |
| E-28 | Duplikat-Schwellen | Startwerte 0.40 (Kandidat) / 0.85 (starker Treffer) bleiben; je Tenant über `settings` übersteuerbar; Validierung mit realen Pilotdaten in M2 (E-51). |
| E-29 | SLA-Zeitbasis | Kalenderstunden (Phase 1 final); Geschäftszeiten-Modell ist Backlog. |
| E-30 | Benachrichtigungen | In-App-Polling 60 s in Phase 1; SSE-Evaluation nach Lasttest in M3. |

### Produkte, Import/Export, API

| ID | Thema | Entscheidung |
|----|-------|--------------|
| E-31 | Mengenstaffeln | `min_quantity`-Staffelpreise sind Phase-2-Backlog; das Positions-Snapshot-Prinzip bleibt unberührt. |
| E-32 | lost_reason | Freitext in Phase 1; tenant-konfigurierbare Auswahlliste in Phase 2 (bestehende Freitexte bleiben erhalten). |
| E-33 | CSV-Encoding | UTF-8 ist Default (mit BOM-Erkennung); der Mapping-Dialog bietet ab Phase 1 zusätzlich Windows-1252/Latin-1 als explizite Auswahl an. |
| E-34 | Job-Benachrichtigung | Import-/Export-Abschluss erzeugt ab M2 eine In-App-Notification (`notifications`, E-13); E-Mail-Benachrichtigung Phase 2. Polling bleibt Basis-Mechanismus. |
| E-35 | Export-Limits | Max. **2 parallele Export-Jobs je Tenant** (weitere werden eingereiht) und max. **10 Export-Jobs pro Stunde je Tenant** (`429 quota_exceeded`). |
| E-36 | Export-Ausführung | Exporte laufen **immer asynchron** über `export_jobs` — auch für kleine Datenmengen (einheitlicher Pfad, planbare Last). |
| E-37 | totalCount | Nur auf Anfrage (`includeTotal=true`) als exakter `COUNT(*)`; Default ohne totalCount. Re-Evaluation nach Lasttests. |
| E-38 | PATCH-Semantik | Nur JSON Merge Patch (RFC 7386). Listen-Umsortierung über dedizierte Endpunkte (z. B. `PUT /pipelines/{id}/stages/order`); kein JSON Patch (RFC 6902). |
| E-39 | Rate Limiting | Phase 1 in-memory je Backend-Instanz mit konservativen Limits (Replikazahl eingerechnet); Durchsetzung im Backend, nicht am Ingress. Service-Client `opencrm-api`: 600 Requests/min je Client. DB-gestützter Zähler erst bei nachgewiesenem Bedarf. |
| E-40 | Volltextsuche | `q`-Parameter je Ressource mit `ILIKE` + `pg_trgm` in Phase 1; zentraler Such-Endpunkt (PostgreSQL FTS) Phase 2. |
| E-41 | Tenant-API-Clients | Mandantenspezifische `client_credentials`-Clients sind Phase 2 (Design: ein confidential Client je Tenant in Keycloak mit `tenant_id`-Claim). Phase 1: nur plattformweiter Client `opencrm-api`. |

### Architektur & Betrieb

| ID | Thema | Entscheidung |
|----|-------|--------------|
| E-42 | KPI-Tagesgrenzen | Strikt **UTC** (Phase 1 final); konfigurierbare Berichts-Zeitzone je Tenant ist eine spätere Ausbaustufe (erfordert MV-Umbau). |
| E-43 | Scheduler-Koordination | **ShedLock mit JDBC-Provider (PostgreSQL-Locktabelle)** für alle wiederkehrenden Jobs (MV-Refresh, SLA-Checks, Cleanup) bei mehreren Replikas; Spring-Batch-Läufe zusätzlich über den Job-Tabellen-Status serialisiert. Ersetzt den früheren Advisory-Lock-Ansatz in Kapitel 09. |
| E-44 | SMTP | Managed-E-Mail-Dienst (EU-Anbieter) als SMTP-Relay; Phase 1 nur Versand (Zuweisungs-/SLA-Mails), Bounce-Handling Phase 2. Anbieterwahl zusammen mit E-45. |
| E-45 | Cloud-Provider | Entscheidung bis **Ende M2** (Kriterien: EU-Region, Managed PostgreSQL mit PITR, Kosten). Bis dahin: Entwicklung auf Docker Compose; CloudNativePG bleibt Fallback, falls kein Managed-Angebot passt. |
| E-46 | Keycloak-Betrieb | Eigenes Deployment über den **Keycloak-Operator** mit externer PostgreSQL; kein Managed-Keycloak. |
| E-47 | mTLS | NetworkPolicies-only in Phase 1 (bestätigt); Service-Mesh-Bedarf wird nach Threat-Model-Review in M3 bewertet. |
| E-48 | Schlüsselverwaltung | Provider-KMS für Backup-/Storage-Verschlüsselung, jährliche Rotation; kein eigenes Vault in Phase 1. |
| E-49 | Staging-Zeitpunkt | Staging auf Kubernetes wird **Ende M2** aufgebaut (früheres Betriebs-Feedback vor M3). |
| E-74 | Repository-Struktur | **Monorepo**: `backend/`, `frontend/`, `infra/`, `docs/` in diesem Repository. |

### ADR-Folgeentscheidungen

| ID | Thema | Entscheidung |
|----|-------|--------------|
| E-55 | Partitionierung (ADR-001) | Zeitbasierte Range-Partitionierung (monatlich) für `activities` und `audit_log`; Aktivierung erst ab Schwellwert E-22, keine Vorab-DDL in M1. |
| E-56 | Pool-Tuning (ADR-001) | Lasttest-Task in M2 (Testcontainers + k6); bis dahin HikariCP-Default max. 20 Connections je Instanz. |
| E-57 | Tenant-Restore-Runbook (ADR-002) | Wird in M3 erstellt (baut auf DSGVO-Vollexport und Offboarding-Prozess auf). |
| E-58 | Dedizierte Instanz (ADR-002) | Trigger: ein Tenant verursacht > 20 % der Plattformlast über 30 Tage oder vertragliche Anforderung; Umzugsprozess wird dann konzipiert. |
| E-59 | Per-Tenant-Query-Statistik (ADR-002) | Kein `pg_stat_statements`-Labeling je Tenant in Phase 1; Tenant-Zuordnung über OpenTelemetry-Traces (`tenant_id`-Attribut). |
| E-60 | Großkunden-Realm (ADR-003) | Eigener Realm nur als Enterprise-Vertragsoption bei konkretem Vertrag; Standard bleibt Single-Realm mit Organizations. |
| E-61 | Keycloak-Config-as-Code (ADR-003) | **keycloak-config-cli** (idempotenter Import in CI); Realm-Konfiguration unter `infra/keycloak/`. |
| E-62 | Worker-Profil (ADR-004) | Spring-Profile `web`/`worker` von Beginn an vorgesehen; separates Worker-Deployment erst bei Importlast-Problemen (Kriterien E-70). |
| E-63 | Virtual Threads (ADR-004) | In M1 deaktiviert; Test unter RLS-/HikariCP-Last in M2, dann Entscheidung. |
| E-64 | Modulith-Verifikation (ADR-004) | Spring-Modulith-Verifikation ist ab M1 **Build-Fehler** (strikt) in der CI. |
| E-65 | include-Parameter (ADR-005) | Keine `include`-Parameter zum v1-Start; Compound-Endpoints erst nach SPA-Messungen in M2. |
| E-66 | Webhooks (ADR-005) | Ja — Ausbaustufe nach M3: `lead.created`, `lead.assigned`, `opportunity.won`, `opportunity.lost` mit HMAC-Signatur. |
| E-67 | Deprecation-Policy (ADR-005) | 6 Monate Ankündigungsfrist, `Deprecation`-/`Sunset`-Header; verbindlich dokumentiert vor dem ersten externen API-Partner. |
| E-68 | Batch-Parameter (ADR-006) | Poll-Intervall 5 s; max. 2 parallele Import-Jobs je Tenant, 4 je Instanz; Kalibrierung unter Last in M2. |
| E-69 | Job-Aufbewahrung (ADR-006) | `import_job_errors` 90 Tage, Spring-Batch-Metadaten 30 Tage, Import-/Exportdateien im Objekt-Storage 30 Tage; täglicher Cleanup-Job. |
| E-70 | Broker-Kriterium (ADR-006) | Re-Evaluation Message-Broker, wenn Job-Wartezeit p95 > 5 min über 7 Tage trotz Worker-Deployment oder wenn Webhooks (E-66) eventgetriebene Zustellung brauchen. |
| E-71 | Token-Handling SPA (ADR-007) | Access-Token nur in-memory, Silent-Refresh, Refresh-Token-Rotation aktiviert. |
| E-72 | E2E-Tests (ADR-007) | **Playwright**; Kern-Flows: Login, Lead-Anlage mit Zuweisung, Import-Dry-Run, Dashboard-Abruf. |
| E-73 | ADR-Prozess (adr/README) | Neue ADRs per Pull Request, Review durch mind. eine Entwicklerin/einen Entwickler, Freigabe durch Lead Architecture; ADRs bleiben in `docs/adr/` (Monorepo, E-74). |
| E-75 | Team-Durchschnitt (Kap. 09) | Der anonymisierte Team-Durchschnitt für sales-rep wird nur ab einer Teamgröße von **mindestens 3 Mitgliedern** ausgeliefert (sonst ausgeblendet), damit keine Rückschlüsse auf Einzelpersonen möglich sind. |

## 3. Terminierte externe Aufgaben (EXTERN — nicht blockierend)

| ID | Thema | Zuständig | Bis |
|----|-------|-----------|-----|
| E-50 | Startdatum & Staffing fixieren, Kalenderplan aus Dauer-Indikationen ableiten | Product Owner | vor M1-Start |
| E-51 | Pilot-Mandanten benennen (reale Importdateien für M2-Datenqualitätstests) | Product Owner | Ende M1 |
| E-52 | Security-Review-Budget: internes RLS-Design-Review in M1 (steht), externer Pentest in M3 | Product Owner | Ende M2 |
| E-53 | Produktions-Domains, SLA-Vertragsdetails (Wartungsfenster, Supportzeiten, Eskalation) | Product Owner / Vertrieb | vor erstem zahlenden Mandanten |
| E-54 | Mandanten-Onboarding: Provisionierung läuft in Phase 1 über einen Admin-API-Aufruf (legt Keycloak-Organization, Tenant und Seeds in einem Schritt an), ausgelöst manuell durch platform-admin. Self-Service-Registrierung ist spätere Ausbaustufe. | festgelegt (ARCH) | — |
| E-02f | Juristische Bestätigung DSGVO-Fristen/Anonymisierung und AVV-Muster | Datenschutzbeauftragter | vor M3-Abschluss |

## Offene Punkte

Keine — alle bekannten offenen Punkte sind entschieden oder terminiert (Abschnitt 3).
