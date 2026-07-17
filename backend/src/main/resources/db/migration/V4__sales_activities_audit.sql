-- M2: Produkte, Preislisten, Pipelines, Opportunities, Aktivitaeten, Audit-Log,
-- Benachrichtigungen und Export-Jobs (docs/03-datenmodell.md, docs/07, docs/08).

-- ---------------------------------------------------------------------------
-- Produkte und Preislisten
-- ---------------------------------------------------------------------------
CREATE TABLE products (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   uuid NOT NULL REFERENCES tenants (id),
    sku         text NOT NULL,
    name        text NOT NULL,
    description text,
    category    text,
    unit        text,
    list_price  numeric(12,2) NOT NULL,
    currency    char(3) NOT NULL,
    tax_rate    numeric(5,2),
    active      boolean NOT NULL DEFAULT true,
    external_id text,
    deleted_at  timestamptz,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, sku)
);
CREATE UNIQUE INDEX uq_products_tenant_external_id ON products (tenant_id, external_id) WHERE external_id IS NOT NULL;
CREATE INDEX idx_products_tenant_active ON products (tenant_id, active) WHERE deleted_at IS NULL;
CREATE INDEX idx_products_tenant_created ON products (tenant_id, created_at DESC, id DESC) WHERE deleted_at IS NULL;
CREATE TRIGGER trg_products_updated_at BEFORE UPDATE ON products
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE price_lists (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id  uuid NOT NULL REFERENCES tenants (id),
    name       text NOT NULL,
    currency   char(3) NOT NULL,
    valid_from date,
    valid_to   date,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CHECK (valid_from IS NULL OR valid_to IS NULL OR valid_from <= valid_to)
);
CREATE TRIGGER trg_price_lists_updated_at BEFORE UPDATE ON price_lists
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE price_list_items (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    price_list_id uuid NOT NULL REFERENCES price_lists (id) ON DELETE CASCADE,
    product_id    uuid NOT NULL REFERENCES products (id),
    unit_price    numeric(12,2) NOT NULL,
    UNIQUE (price_list_id, product_id)
);

ALTER TABLE accounts ADD COLUMN price_list_id uuid REFERENCES price_lists (id);

-- ---------------------------------------------------------------------------
-- Pipelines und Stages
-- ---------------------------------------------------------------------------
CREATE TABLE pipelines (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id  uuid NOT NULL REFERENCES tenants (id),
    name       text NOT NULL,
    is_default boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_pipelines_tenant_default ON pipelines (tenant_id) WHERE is_default;
CREATE TRIGGER trg_pipelines_updated_at BEFORE UPDATE ON pipelines
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE pipeline_stages (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    pipeline_id uuid NOT NULL REFERENCES pipelines (id) ON DELETE CASCADE,
    name        text NOT NULL,
    sort_order  integer NOT NULL,
    probability numeric(5,2) NOT NULL CHECK (probability >= 0 AND probability <= 100),
    is_won      boolean NOT NULL DEFAULT false,
    is_lost     boolean NOT NULL DEFAULT false,
    UNIQUE (pipeline_id, sort_order),
    CHECK (NOT (is_won AND is_lost))
);

-- ---------------------------------------------------------------------------
-- Opportunities
-- ---------------------------------------------------------------------------
CREATE TABLE opportunities (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           uuid NOT NULL REFERENCES tenants (id),
    account_id          uuid NOT NULL REFERENCES accounts (id),
    pipeline_id         uuid NOT NULL REFERENCES pipelines (id),
    stage_id            uuid NOT NULL REFERENCES pipeline_stages (id),
    name                text NOT NULL,
    amount              numeric(14,2) NOT NULL DEFAULT 0,
    is_estimated        boolean NOT NULL DEFAULT false,
    currency            char(3) NOT NULL,
    expected_close_date date,
    owner_id            uuid REFERENCES users (id),
    lead_id             uuid REFERENCES leads (id),
    status              text NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'WON', 'LOST')),
    won_at              timestamptz,
    lost_at             timestamptz,
    lost_reason         text,
    deleted_at          timestamptz,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_opportunities_tenant_status ON opportunities (tenant_id, status) WHERE deleted_at IS NULL;
CREATE INDEX idx_opportunities_open_stage ON opportunities (tenant_id, stage_id) WHERE status = 'OPEN' AND deleted_at IS NULL;
CREATE INDEX idx_opportunities_tenant_owner ON opportunities (tenant_id, owner_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_opportunities_tenant_won ON opportunities (tenant_id, won_at) WHERE status = 'WON';
CREATE INDEX idx_opportunities_tenant_created ON opportunities (tenant_id, created_at DESC, id DESC) WHERE deleted_at IS NULL;
CREATE TRIGGER trg_opportunities_updated_at BEFORE UPDATE ON opportunities
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

ALTER TABLE leads ADD CONSTRAINT fk_leads_converted_opportunity
    FOREIGN KEY (converted_opportunity_id) REFERENCES opportunities (id);

CREATE TABLE opportunity_items (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      uuid NOT NULL REFERENCES tenants (id),
    opportunity_id uuid NOT NULL REFERENCES opportunities (id) ON DELETE CASCADE,
    product_id     uuid NOT NULL REFERENCES products (id),
    quantity       numeric(12,3) NOT NULL CHECK (quantity > 0),
    unit_price     numeric(12,2) NOT NULL,
    discount_pct   numeric(5,2) NOT NULL DEFAULT 0 CHECK (discount_pct >= 0 AND discount_pct <= 100),
    position       integer NOT NULL,
    UNIQUE (opportunity_id, position)
);
CREATE INDEX idx_opportunity_items_opportunity ON opportunity_items (tenant_id, opportunity_id);

-- ---------------------------------------------------------------------------
-- Aktivitaeten
-- ---------------------------------------------------------------------------
CREATE TABLE activities (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      uuid NOT NULL REFERENCES tenants (id),
    type           text NOT NULL CHECK (type IN ('CALL', 'EMAIL', 'MEETING', 'NOTE', 'TASK')),
    subject        text NOT NULL,
    body           text,
    due_at         timestamptz,
    completed_at   timestamptz,
    owner_id       uuid REFERENCES users (id),
    lead_id        uuid REFERENCES leads (id),
    account_id     uuid REFERENCES accounts (id),
    contact_id     uuid REFERENCES contacts (id),
    opportunity_id uuid REFERENCES opportunities (id),
    deleted_at     timestamptz,
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_activities_tenant_owner ON activities (tenant_id, owner_id, created_at DESC);
CREATE INDEX idx_activities_lead ON activities (tenant_id, lead_id, created_at DESC) WHERE lead_id IS NOT NULL;
CREATE INDEX idx_activities_account ON activities (tenant_id, account_id, created_at DESC) WHERE account_id IS NOT NULL;
CREATE INDEX idx_activities_opportunity ON activities (tenant_id, opportunity_id, created_at DESC) WHERE opportunity_id IS NOT NULL;
CREATE INDEX idx_activities_open_tasks ON activities (tenant_id, owner_id, due_at)
    WHERE completed_at IS NULL AND deleted_at IS NULL;
CREATE TRIGGER trg_activities_updated_at BEFORE UPDATE ON activities
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ---------------------------------------------------------------------------
-- Audit-Log, Benachrichtigungen, Export-Jobs
-- ---------------------------------------------------------------------------
CREATE TABLE audit_log (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   uuid NOT NULL REFERENCES tenants (id),
    actor_id    uuid,
    entity_type text NOT NULL,
    entity_id   uuid,
    action      text NOT NULL CHECK (action IN ('CREATE', 'UPDATE', 'DELETE', 'ASSIGN', 'IMPORT', 'EXPORT', 'LOGIN')),
    diff        jsonb,
    occurred_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_log_tenant_time ON audit_log (tenant_id, occurred_at DESC);
CREATE INDEX idx_audit_log_entity ON audit_log (tenant_id, entity_type, entity_id);

CREATE TABLE notifications (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id  uuid NOT NULL REFERENCES tenants (id),
    user_id    uuid NOT NULL REFERENCES users (id),
    type       text NOT NULL,
    payload    jsonb NOT NULL DEFAULT '{}',
    read_at    timestamptz,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_notifications_user ON notifications (tenant_id, user_id, created_at DESC) WHERE read_at IS NULL;

CREATE TABLE export_jobs (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           uuid NOT NULL REFERENCES tenants (id),
    entity_type         text NOT NULL CHECK (entity_type IN ('LEAD', 'ACCOUNT', 'CONTACT', 'PRODUCT')),
    format              text NOT NULL CHECK (format IN ('CSV', 'XLSX', 'JSON')),
    filter              jsonb NOT NULL DEFAULT '{}',
    status              text NOT NULL DEFAULT 'PENDING'
                        CHECK (status IN ('PENDING', 'VALIDATING', 'RUNNING', 'COMPLETED',
                                          'COMPLETED_WITH_ERRORS', 'FAILED', 'CANCELLED')),
    file_path           text,
    row_count           integer,
    download_expires_at timestamptz,
    created_by          uuid REFERENCES users (id),
    started_at          timestamptz,
    finished_at         timestamptz,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_export_jobs_tenant ON export_jobs (tenant_id, created_at DESC);
CREATE TRIGGER trg_export_jobs_updated_at BEFORE UPDATE ON export_jobs
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ---------------------------------------------------------------------------
-- RLS
-- ---------------------------------------------------------------------------
DO $$
DECLARE
    t text;
BEGIN
    FOREACH t IN ARRAY ARRAY['products', 'price_lists', 'pipelines', 'opportunities', 'opportunity_items',
                             'activities', 'audit_log', 'notifications', 'export_jobs']
    LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
        EXECUTE format(
            'CREATE POLICY tenant_isolation ON %I FOR ALL '
            || 'USING (tenant_id = NULLIF(current_setting(''app.current_tenant'', true), '''')::uuid) '
            || 'WITH CHECK (tenant_id = NULLIF(current_setting(''app.current_tenant'', true), '''')::uuid)', t);
    END LOOP;
END $$;

-- Tabellen ohne tenant_id: Isolation ueber die Eltern-Tabelle
ALTER TABLE pipeline_stages ENABLE ROW LEVEL SECURITY;
ALTER TABLE pipeline_stages FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON pipeline_stages FOR ALL
    USING (EXISTS (SELECT 1 FROM pipelines p WHERE p.id = pipeline_stages.pipeline_id))
    WITH CHECK (EXISTS (SELECT 1 FROM pipelines p WHERE p.id = pipeline_stages.pipeline_id));

ALTER TABLE price_list_items ENABLE ROW LEVEL SECURITY;
ALTER TABLE price_list_items FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON price_list_items FOR ALL
    USING (EXISTS (SELECT 1 FROM price_lists pl WHERE pl.id = price_list_items.price_list_id))
    WITH CHECK (EXISTS (SELECT 1 FROM price_lists pl WHERE pl.id = price_list_items.price_list_id));
