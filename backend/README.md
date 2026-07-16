# OpenCRM Backend

Modularer Monolith (Spring Boot 3.3, Java 21). Architektur: siehe [docs/02-systemarchitektur.md](../docs/02-systemarchitektur.md).

## Voraussetzungen

- Java 21, Maven 3.9+
- Docker (für die Dev-Infrastruktur und die Testcontainers-Integrationstests)

## Entwicklung starten

```bash
# 1. Infrastruktur (PostgreSQL 5432, Keycloak 8081, MinIO 9000)
cd infra && docker compose up -d

# 2. Dev-Tenant anlegen (einmalig; UUID passend zum Keycloak-Dev-Nutzer)
docker exec -i opencrm-postgres-1 psql -U postgres -d opencrm -c \
  "INSERT INTO tenants (id, name, slug) VALUES ('00000000-0000-0000-0000-000000000001', 'Dev Tenant', 'dev') ON CONFLICT DO NOTHING;"

# 3. Backend starten
cd ../backend && mvn spring-boot:run
```

Login-Daten für die Dev-Umgebung: Keycloak-Admin `admin`/`admin` (http://localhost:8081), Realm-Nutzer `dev`/`dev` (Rolle `tenant-admin`, Tenant `00000000-…-01`).

Token für API-Tests holen (Direct Access Grant ist im Dev-Realm aktiviert):

```bash
TOKEN=$(curl -s http://localhost:8081/realms/opencrm/protocol/openid-connect/token \
  -d grant_type=password -d client_id=opencrm-web -d username=dev -d password=dev \
  | python3 -c "import json,sys; print(json.load(sys.stdin)['access_token'])")
curl -s http://localhost:8080/api/v1/leads -H "Authorization: Bearer $TOKEN"
```

## Tests

```bash
mvn verify
```

Die Integrationstests (u. a. `RlsIsolationTest`) starten ein echtes PostgreSQL 16 über Testcontainers und beweisen die Mandanten-Isolation: App-Rolle ohne `BYPASSRLS`, fail-closed ohne Tenant-Kontext, Fremd-Tenant-INSERT wird von der Policy abgelehnt. Der Modulschnitt wird durch `ModularityTest` (Spring Modulith) erzwungen — Verstöße sind Build-Fehler (E-64).

## Struktur

| Modul | Inhalt |
|---|---|
| `shared` | Tenant-Kontext + RLS-Anbindung (Hibernate MultiTenantConnectionProvider), Security (OAuth2 Resource Server, Rollen-Mapping) |
| `tenant` | Mandanten-Stammdaten (Provisionierung folgt) |
| `lead` | Lead-Entität, Repository, erste REST-Endpunkte |

Datenbankschema: Flyway-Migrationen unter `src/main/resources/db/migration/` — `V1__baseline.sql` legt die M1-Kerntabellen inkl. Row-Level-Security-Policies an. Rollen (`opencrm_migrator`, `opencrm_app`) werden außerhalb von Flyway provisioniert (`infra/postgres/init/01-roles.sql`).
