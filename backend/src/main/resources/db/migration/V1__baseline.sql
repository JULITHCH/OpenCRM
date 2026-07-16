-- OpenCRM Baseline: Kerntabellen fuer M1 + Row-Level Security.
-- Laeuft als opencrm_migrator (Schema-Owner). Die Anwendung verbindet sich als
-- opencrm_app (ohne BYPASSRLS); beide Rollen werden ausserhalb von Flyway angelegt
-- (infra/postgres/init bzw. Betriebs-Provisionierung), da Rollen clusterweit sind.

-- Benoetigte Extensions (pg_trgm) werden bei der DB-Provisionierung als Superuser
-- angelegt (infra/postgres/init/01-roles.sql) — der Migrator hat dafuer bewusst keine Rechte.

-- ---------------------------------------------------------------------------
-- Hilfsfunktion: updated_at automatisch pflegen
-- ---------------------------------------------------------------------------
CREATE FUNCTION set_updated_at() RETURNS trigger AS $$
BEGIN
    NEW.updated_at := now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ---------------------------------------------------------------------------
-- tenants (plattformglobal, kein tenant_id)
-- ---------------------------------------------------------------------------
CREATE TABLE tenants (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name             text NOT NULL,
    slug             text NOT NULL UNIQUE,
    status           text NOT NULL DEFAULT 'ACTIVE'
                     CHECK (status IN ('ACTIVE', 'SUSPENDED', 'OFFBOARDING')),
    plan             text NOT NULL DEFAULT 'standard',
    default_currency char(3) NOT NULL DEFAULT 'EUR',
    settings         jsonb NOT NULL DEFAULT '{}',
    created_at       timestamptz NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------------
-- users (Spiegel der Keycloak-Nutzer, JIT-provisioniert)
-- ---------------------------------------------------------------------------
CREATE TABLE users (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    uuid NOT NULL REFERENCES tenants (id),
    keycloak_id  text NOT NULL UNIQUE,
    email        text NOT NULL,
    display_name text NOT NULL,
    role         text NOT NULL
                 CHECK (role IN ('platform-admin', 'tenant-admin', 'sales-manager', 'sales-rep', 'read-only')),
    active       boolean NOT NULL DEFAULT true,
    created_at   timestamptz NOT NULL DEFAULT now(),
    updated_at   timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_users_tenant ON users (tenant_id, active);
CREATE TRIGGER trg_users_updated_at BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ---------------------------------------------------------------------------
-- teams / team_members
-- ---------------------------------------------------------------------------
CREATE TABLE teams (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id  uuid NOT NULL REFERENCES tenants (id),
    name       text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, name)
);
CREATE TRIGGER trg_teams_updated_at BEFORE UPDATE ON teams
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE team_members (
    team_id    uuid NOT NULL REFERENCES teams (id) ON DELETE CASCADE,
    user_id    uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    is_lead    boolean NOT NULL DEFAULT false,
    is_primary boolean NOT NULL DEFAULT false,
    PRIMARY KEY (team_id, user_id)
);
-- genau ein Primaerteam je Nutzer (E-19)
CREATE UNIQUE INDEX uq_team_members_primary ON team_members (user_id) WHERE is_primary;

-- ---------------------------------------------------------------------------
-- leads (inkl. Zuweisung, Konvertierungs-Zeitpunkte, external_id)
-- ---------------------------------------------------------------------------
CREATE TABLE leads (
    id                       uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                uuid NOT NULL REFERENCES tenants (id),
    title                    text,
    company_name             text,
    first_name               text,
    last_name                text,
    email                    text,
    phone                    text,
    source                   text NOT NULL DEFAULT 'MANUAL'
                             CHECK (source IN ('WEB_FORM', 'IMPORT', 'MANUAL', 'API', 'EVENT', 'REFERRAL')),
    status                   text NOT NULL DEFAULT 'NEW'
                             CHECK (status IN ('NEW', 'ASSIGNED', 'CONTACTED', 'QUALIFIED', 'DISQUALIFIED', 'CONVERTED')),
    score                    integer,
    owner_id                 uuid REFERENCES users (id),
    disqualified_reason      text,
    converted_at             timestamptz,
    disqualified_at          timestamptz,
    converted_account_id     uuid,
    converted_contact_id     uuid,
    converted_opportunity_id uuid,
    external_id              text,
    custom                   jsonb NOT NULL DEFAULT '{}',
    deleted_at               timestamptz,
    created_at               timestamptz NOT NULL DEFAULT now(),
    updated_at               timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT leads_name_present CHECK (company_name IS NOT NULL OR last_name IS NOT NULL)
);
CREATE UNIQUE INDEX uq_leads_tenant_external_id ON leads (tenant_id, external_id) WHERE external_id IS NOT NULL;
CREATE INDEX idx_leads_tenant_status ON leads (tenant_id, status) WHERE deleted_at IS NULL;
CREATE INDEX idx_leads_tenant_owner ON leads (tenant_id, owner_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_leads_tenant_email ON leads (tenant_id, email) WHERE email IS NOT NULL;
CREATE TRIGGER trg_leads_updated_at BEFORE UPDATE ON leads
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ---------------------------------------------------------------------------
-- lead_assignments (Zuweisungshistorie)
-- ---------------------------------------------------------------------------
CREATE TABLE lead_assignments (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   uuid NOT NULL REFERENCES tenants (id),
    lead_id     uuid NOT NULL REFERENCES leads (id) ON DELETE CASCADE,
    assigned_to uuid NOT NULL REFERENCES users (id),
    assigned_by uuid REFERENCES users (id),
    method      text NOT NULL CHECK (method IN ('MANUAL', 'ROUND_ROBIN', 'RULE')),
    rule_id     uuid,
    assigned_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_lead_assignments_lead ON lead_assignments (tenant_id, lead_id, assigned_at);

-- ---------------------------------------------------------------------------
-- assignment_rules
-- ---------------------------------------------------------------------------
CREATE TABLE assignment_rules (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   uuid NOT NULL REFERENCES tenants (id),
    name        text NOT NULL,
    priority    integer NOT NULL,
    active      boolean NOT NULL DEFAULT true,
    criteria    jsonb NOT NULL DEFAULT '{}',
    target_type text NOT NULL CHECK (target_type IN ('USER', 'TEAM')),
    target_id   uuid NOT NULL,
    strategy    text NOT NULL DEFAULT 'DIRECT' CHECK (strategy IN ('DIRECT', 'ROUND_ROBIN')),
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, priority)
);
CREATE TRIGGER trg_assignment_rules_updated_at BEFORE UPDATE ON assignment_rules
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ---------------------------------------------------------------------------
-- round_robin_pointers (E-13)
-- ---------------------------------------------------------------------------
CREATE TABLE round_robin_pointers (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    uuid NOT NULL REFERENCES tenants (id),
    team_id      uuid NOT NULL REFERENCES teams (id) ON DELETE CASCADE,
    last_user_id uuid REFERENCES users (id),
    updated_at   timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, team_id)
);

-- ---------------------------------------------------------------------------
-- Row-Level Security
-- ---------------------------------------------------------------------------
-- Policies vergleichen gegen die Session-Variable app.current_tenant.
-- NULLIF faengt den Fall "Variable nach RESET leer" ab: der Vergleich wird NULL,
-- die Policy liefert keine Zeilen (fail-closed).

-- tenants: Lesen fuer die App erlaubt (Provisionierung/Tenant-Aufloesung),
-- Schreiben nur ueber den auditierten Provisionierungspfad der Anwendung.
ALTER TABLE tenants ENABLE ROW LEVEL SECURITY;
ALTER TABLE tenants FORCE ROW LEVEL SECURITY;
CREATE POLICY tenants_select ON tenants FOR SELECT USING (true);
CREATE POLICY tenants_insert ON tenants FOR INSERT WITH CHECK (true);
CREATE POLICY tenants_update ON tenants FOR UPDATE
    USING (id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

-- Mandantengebundene Tabellen: eine FOR-ALL-Policy je Tabelle
DO $$
DECLARE
    t text;
BEGIN
    FOREACH t IN ARRAY ARRAY['users', 'teams', 'leads', 'lead_assignments', 'assignment_rules', 'round_robin_pointers']
    LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
        EXECUTE format(
            'CREATE POLICY tenant_isolation ON %I FOR ALL '
            || 'USING (tenant_id = NULLIF(current_setting(''app.current_tenant'', true), '''')::uuid) '
            || 'WITH CHECK (tenant_id = NULLIF(current_setting(''app.current_tenant'', true), '''')::uuid)', t);
    END LOOP;
END $$;

-- team_members hat kein tenant_id: Isolation ueber das Team
ALTER TABLE team_members ENABLE ROW LEVEL SECURITY;
ALTER TABLE team_members FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON team_members FOR ALL
    USING (EXISTS (SELECT 1 FROM teams WHERE teams.id = team_members.team_id))
    WITH CHECK (EXISTS (SELECT 1 FROM teams WHERE teams.id = team_members.team_id));

-- ---------------------------------------------------------------------------
-- Grants fuer die App-Rolle
-- ---------------------------------------------------------------------------
GRANT USAGE ON SCHEMA public TO opencrm_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO opencrm_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO opencrm_app;
-- Flyway-Historie bleibt der App entzogen
REVOKE ALL ON flyway_schema_history FROM opencrm_app;
