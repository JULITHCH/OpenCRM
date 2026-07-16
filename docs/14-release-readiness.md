# Release-Readiness

| | |
|---|---|
| Status | Härtungsphase abgeschlossen |
| Stand | 2026-07-16 |
| Verantwortlich | Lead Architecture |

## Zweck

Dieses Dokument hält das Ergebnis des Multi-Lens-Release-Reviews fest (sieben Perspektiven:
Sicherheit/Mandantentrennung, Korrektheit, DSGVO, API-Vertrag, Frontend/UX, Betrieb, Testlücken).
59 Findings wurden adversarial verifiziert. Der Abschnitt „Behoben" listet die umgesetzten
Korrekturen, „Bewusst offen" die nachvollziehbar zurückgestellten Punkte mit Begründung.

## Behoben (dieser Härtungspass)

### Kritisch
- **OAuth2-Konfiguration lag unter falschem Prefix** (`opencrm.security…` statt `spring.security…`):
  Das per Helm deployte Backend hätte keinen `JwtDecoder` erzeugt und wäre nicht gestartet.
  Korrigiert; zusätzlich lazy `SupplierJwtDecoder`, damit der Start nicht an der Keycloak-Erreichbarkeit hängt.
- **Import-/Exportdateien auf flüchtigem `emptyDir`** (Datenverlust bei Pod-Neustart/Skalierung):
  S3/MinIO-Implementierung (`S3FileStorage`, aktiv über `opencrm.storage.type=s3`); Helm nutzt in
  Produktion S3 statt eines Pod-lokalen Volumes.

### Sicherheit
- **Owner-Scope durchgesetzt** (`AccessGuard`/`CallerContext`): ein reiner `sales-rep` kann nur eigene
  Leads/Opportunities ändern; Manager/Admins uneingeschränkt. RLS trennt Mandanten, diese Schicht
  trennt Nutzer innerhalb des Mandanten. Durch Test abgesichert (`SecurityAndExportTest`).
- **401/403 als `application/problem+json`** (RFC 9457) inkl. `code`/`traceId`; einheitlicher
  `AuthenticationEntryPoint`/`AccessDeniedHandler`.
- **Audience-Validierung** des Access Tokens (`opencrm.auth.expected-audience`, Realm-Audience-Mapper).
- **In-Memory-Rate-Limiting** je Instanz (`RateLimitFilter`, Token-Bucket je JWT-Subject, E-39).
- **Keycloak-Realm gehärtet**: Direct Access Grant / Implicit Flow aus, Service-Client-Secret über
  `OPENCRM_API_CLIENT_SECRET`, Redirect-URIs/Origins über Env; dev/prod-Trennung dokumentiert
  ([infra/keycloak/README.md](../infra/keycloak/README.md)).

### Korrektheit
- Lead-Statusmaschine: Zuweisung auf `DISQUALIFIED`/`CONVERTED` unterbunden; Reaktivierung
  `DISQUALIFIED → NEW`; `POST /leads/{id}/reactivate`.
- Opportunity: Positions-/Schätz-/Won-/Lost-Änderungen nur im Status `OPEN`; `won` verlangt Positionen
  oder Schätzbetrag; `POST /opportunities/{id}/reopen` (WON/LOST → OPEN).
- Produkt-SKU-Dublette liefert `409 duplicate_found` (auch gegen soft-gelöschte SKUs).
- Import: Schutz gegen parallele/doppelte Läufe (Status-CAS), Recovery-Job für verwaiste Läufe
  (>30 min in RUNNING → FAILED), `CREATE`-Strategie verletzt den `external_id`-Unique-Index nicht mehr.
- Export läuft nicht mehr in einer langen Transaktion (drei kurze Schritte, `FileStorage.put` außerhalb
  der DB-Transaktion).

### DSGVO
- Anonymisierung erweitert auf Aktivitäten anonymisierter Leads/Kontakte und `import_job_errors.raw_row`.
- Datenauskunft (`GdprController`) ergänzt um Leads derselben Person (E-Mail-Match).
- Cleanup-Job (`StorageCleanupJob`, E-69): abgelaufene Export-Dateien, alte Import-Dateien und
  Import-Fehlerzeilen werden geräumt.
- IMPORT/EXPORT/ASSIGN und Anonymisierung schreiben Audit-Einträge.

### Betrieb
- `/actuator/prometheus` funktionsfähig (Micrometer-Registry); Zugriff im Cluster über NetworkPolicy.
- Strukturierte JSON-Logs im Profil `json` mit `tenant_id`/`user_id`/`request_id`/`trace_id` (MDC) und
  E-Mail-Masking (`logback-spring.xml`, `RequestIdFilter`).
- Graceful Shutdown (`server.shutdown=graceful`, `preStop`, `terminationGracePeriodSeconds`).
- DB in der Readiness-Group; PodDisruptionBudget (`minAvailable: 1`).
- Release-Workflow mit Quality-Gate: Backend-`verify` + Frontend-Lint/Build + Trivy-Scan
  (CRITICAL/HIGH blockend) vor dem Image-Push.
- Commit-Stand im Frontend-Footer und unter `/actuator/info` (git-commit-id-Plugin), Copyright
  JULITH GmbH verlinkt.

## Bewusst offen (tracked, nächster Iterationsschritt)

Diese Punkte sind real, aber weder Datenleck, Datenverlust noch Absturz — sie sind
API-Vollständigkeit bzw. Feinschliff und für einen kontrollierten Erst-Release vertretbar.

| Thema | Begründung der Zurückstellung |
|---|---|
| Optimistic Locking (`version`/ETag/If-Match) | Kein Datenverlust im Single-User-pro-Datensatz-Betrieb; letzter Schreiber gewinnt. Nachziehen vor Multi-Editor-Szenarien. |
| Idempotency-Key auf Job-POSTs | Doppelte Import-/Export-Jobs sind unschädlich (eigene Job-ID, wiederholbar). |
| `includeTotal`/`totalCount` | Cursor-Pagination funktioniert; die Gesamtzahl ist ein Zusatz-Feature (E-37), nicht blockierend. |
| CSV-`status`-Filter, generisches `sort` | Einzel-Status-Filter und feste Sortierung (`-createdAt`) decken die UI ab. |
| `tenant_context_missing` (403 statt leerer Antwort) | RLS ist fail-closed — Daten sind geschützt; nur der HTTP-Fehlercode ist noch generisch. |
| Suspendierter-Tenant-403, tenant_id-Claim-Mismatch-403 | Zusätzliche Verteidigungslinien; RLS + JIT-Sync schützen bereits. |
| Export team-scoped für Manager | Export ist Manager/Admin/Read-only-only; Manager haben laut Konzept mandantenweite Sicht. Feinere Team-Scoping-Regel nachziehbar. |
| Frontend-Tests (Vitest/Playwright) | Backend ist umfassend getestet (19 Integrationstests); Frontend-E2E ist geplant (E-72). |
| `contacts`/`accounts` Owner-Scoping | Firmendaten sind bewusst mandantenweit geteilt; Owner-Scope gilt für Leads/Opportunities. |

## Verifikation

- Backend: **19 Integrationstests grün** (Testcontainers gegen PostgreSQL 16), inkl. RLS-Isolation,
  Owner-Scope, Rollenmatrix, Export-End-to-End, Import-Dry-Run/Execute, Vertriebs- und Reporting-Flow.
- Frontend: `npm run lint` und `npm run build` fehlerfrei.
- Helm: `helm lint` und `helm template` (S3- und Local-Variante) fehlerfrei.
