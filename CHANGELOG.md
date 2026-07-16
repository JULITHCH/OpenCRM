# Changelog

Alle nennenswerten Änderungen an OpenCRM. Format angelehnt an [Keep a Changelog](https://keepachangelog.com/de/), Versionierung über Git-Tags (`v*`, siehe `.github/workflows/release.yml`).

## [Unveröffentlicht]

### Release-Härtung (Multi-Lens-Review, 2026-07-16)
- Kritisch: OAuth2-Issuer-Konfiguration korrigiert (Backend startet unter Helm), Import-/Exportdateien auf S3/MinIO statt flüchtigem emptyDir.
- Sicherheit: Owner-Scope für sales-rep durchgesetzt, 401/403 als problem+json, Audience-Validierung, In-Memory-Rate-Limiting, Keycloak-Realm gehärtet.
- Korrektheit: Lead-/Opportunity-Statusmaschinen abgesichert, SKU-Dublette 409, Import-Robustheit (CAS, Recovery-Job), Export ohne Langtransaktion.
- DSGVO: Anonymisierung/Datenauskunft vollständiger, Storage-Cleanup-Job, Audit-Einträge.
- Betrieb: Prometheus-Metriken, JSON-Logs mit MDC, Graceful Shutdown, PodDisruptionBudget, Release-Quality-Gate mit Trivy.
- Commit-Stand im Footer und `/actuator/info`; Copyright JULITH GmbH.
- Details und bewusst zurückgestellte Punkte: [docs/14-release-readiness.md](docs/14-release-readiness.md).

### Hinzugefügt
- **M1 — Fundament:** Mandantenfähigkeit über PostgreSQL Row-Level Security (fail-closed), Keycloak-Authentifizierung (OIDC/PKCE, JIT-User-Provisionierung), Tenant-Provisionierung mit Seeds, Accounts/Kontakte/Leads mit Cursor-Pagination, manuelle Lead-Zuweisung mit Historie, CSV-Import (Mapping-Vorschlag, Dry-Run, Fehlerbericht), Docker-Compose-Dev-Umgebung, CI.
- **M2 — Vertrieb:** Produktkatalog, Preislisten mit Auflösungsreihenfolge, konfigurierbare Pipelines, Opportunities mit Positionen/Schätzbetrag/Won/Lost, regelbasierte Lead-Zuweisung mit Round-Robin je Team, Lead-Konvertierung, Aktivitäten, Audit-Log, XLSX-Import, Exporte (CSV/XLSX/JSON) mit Tenant-Limits.
- **M3 — Insights & Reife:** Verkäufer-Dashboard (KPIs, Umsatz-Zeitreihe, Pipeline-Funnel, Leaderboard) mit rollenbasierten Sichtbarkeits-Scopes, materialisierte Sicht mit ShedLock-Refresh, SLA-Eskalation mit In-App-Benachrichtigungen, DSGVO-Anonymisierung und -Datenauskunft, Tenant-Settings, Frontend komplett (inkl. Import-Wizard), Dockerfiles, Helm-Chart, Release-Pipeline.
