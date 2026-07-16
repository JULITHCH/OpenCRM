-- Cluster-Rollen fuer OpenCRM (laufen einmalig beim Initialisieren der Dev-/Test-Datenbank).
-- opencrm_migrator: fuehrt Flyway-Migrationen aus, besitzt die Schema-Objekte.
-- opencrm_app:      Laufzeit-Rolle der Anwendung, KEIN BYPASSRLS — RLS greift immer.
-- Passwoerter gelten nur fuer Dev/Test; in Staging/Prod kommen sie aus dem Secret-Store.

CREATE ROLE opencrm_migrator LOGIN PASSWORD 'opencrm_migrator';
CREATE ROLE opencrm_app LOGIN PASSWORD 'opencrm_app' NOBYPASSRLS;

GRANT CONNECT ON DATABASE opencrm TO opencrm_migrator, opencrm_app;
GRANT USAGE, CREATE ON SCHEMA public TO opencrm_migrator;
GRANT USAGE ON SCHEMA public TO opencrm_app;

-- Extensions brauchen Superuser-Rechte und werden deshalb hier statt in Flyway angelegt
CREATE EXTENSION IF NOT EXISTS pg_trgm;
