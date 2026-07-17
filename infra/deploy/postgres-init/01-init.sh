#!/bin/bash
# Einmalige DB-Initialisierung fuer den Single-Host-Betrieb (laeuft nur beim ersten
# Start mit leerem Daten-Volume). Legt die OpenCRM-Rollen mit Passwoertern aus der
# Umgebung an, aktiviert pg_trgm und richtet eine separate Keycloak-Datenbank ein.
set -euo pipefail

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname opencrm <<SQL
-- Laufzeit-Rolle (KEIN BYPASSRLS) und Migrator-Rolle (Schema-Owner, Flyway)
CREATE ROLE opencrm_migrator LOGIN PASSWORD '${OPENCRM_DB_MIGRATOR_PASSWORD}';
CREATE ROLE opencrm_app LOGIN PASSWORD '${OPENCRM_DB_APP_PASSWORD}' NOBYPASSRLS;
GRANT opencrm_app TO opencrm_migrator;

GRANT CONNECT ON DATABASE opencrm TO opencrm_migrator, opencrm_app;
GRANT USAGE, CREATE ON SCHEMA public TO opencrm_migrator;
GRANT USAGE, CREATE ON SCHEMA public TO opencrm_app;

CREATE EXTENSION IF NOT EXISTS pg_trgm;
SQL

# Separate Datenbank + Rolle fuer Keycloak (eigene Persistenz, nicht mit OpenCRM gemischt)
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres <<SQL
CREATE ROLE keycloak LOGIN PASSWORD '${KEYCLOAK_DB_PASSWORD}';
CREATE DATABASE keycloak OWNER keycloak;
SQL

echo "OpenCRM-DB-Init abgeschlossen (Rollen, pg_trgm, Keycloak-DB)."
