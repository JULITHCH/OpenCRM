-- Import-Infrastruktur (docs/08-import-export.md): Jobs, Fehlerzeilen, Mapping-Vorlagen.

CREATE TABLE import_jobs (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      uuid NOT NULL REFERENCES tenants (id),
    entity_type    text NOT NULL CHECK (entity_type IN ('LEAD', 'ACCOUNT', 'CONTACT', 'PRODUCT')),
    file_name      text NOT NULL,
    storage_key    text NOT NULL,
    format         text NOT NULL CHECK (format IN ('CSV', 'XLSX')),
    mapping        jsonb NOT NULL DEFAULT '{}',
    options        jsonb NOT NULL DEFAULT '{}',
    mode           text NOT NULL DEFAULT 'DRY_RUN' CHECK (mode IN ('DRY_RUN', 'EXECUTE')),
    status         text NOT NULL DEFAULT 'PENDING'
                   CHECK (status IN ('PENDING', 'VALIDATING', 'RUNNING', 'COMPLETED',
                                     'COMPLETED_WITH_ERRORS', 'FAILED', 'CANCELLED')),
    total_rows     integer,
    processed_rows integer NOT NULL DEFAULT 0,
    error_rows     integer NOT NULL DEFAULT 0,
    created_by     uuid REFERENCES users (id),
    started_at     timestamptz,
    finished_at    timestamptz,
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_import_jobs_tenant ON import_jobs (tenant_id, created_at DESC);
CREATE TRIGGER trg_import_jobs_updated_at BEFORE UPDATE ON import_jobs
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE import_job_errors (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    import_job_id uuid NOT NULL REFERENCES import_jobs (id) ON DELETE CASCADE,
    row_number    integer NOT NULL,
    column_name   text,
    error_code    text NOT NULL,
    message       text NOT NULL,
    raw_row       jsonb
);
CREATE INDEX idx_import_job_errors_job ON import_job_errors (import_job_id, row_number);

CREATE TABLE import_mappings (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   uuid NOT NULL REFERENCES tenants (id),
    entity_type text NOT NULL CHECK (entity_type IN ('LEAD', 'ACCOUNT', 'CONTACT', 'PRODUCT')),
    name        text NOT NULL,
    mapping     jsonb NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, entity_type, name)
);
CREATE TRIGGER trg_import_mappings_updated_at BEFORE UPDATE ON import_mappings
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- RLS
DO $$
DECLARE
    t text;
BEGIN
    FOREACH t IN ARRAY ARRAY['import_jobs', 'import_mappings']
    LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
        EXECUTE format(
            'CREATE POLICY tenant_isolation ON %I FOR ALL '
            || 'USING (tenant_id = NULLIF(current_setting(''app.current_tenant'', true), '''')::uuid) '
            || 'WITH CHECK (tenant_id = NULLIF(current_setting(''app.current_tenant'', true), '''')::uuid)', t);
    END LOOP;
END $$;

-- import_job_errors hat kein tenant_id: Isolation ueber den Job
ALTER TABLE import_job_errors ENABLE ROW LEVEL SECURITY;
ALTER TABLE import_job_errors FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON import_job_errors FOR ALL
    USING (EXISTS (SELECT 1 FROM import_jobs j WHERE j.id = import_job_errors.import_job_id))
    WITH CHECK (EXISTS (SELECT 1 FROM import_jobs j WHERE j.id = import_job_errors.import_job_id));
