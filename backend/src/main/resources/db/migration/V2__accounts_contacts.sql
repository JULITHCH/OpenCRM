-- Accounts (Firmen/Kunden) und Contacts (Ansprechpartner) — M1-Umfang.
-- accounts.price_list_id folgt mit dem sales-Modul in M2 (additive Migration).

CREATE TABLE accounts (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   uuid NOT NULL REFERENCES tenants (id),
    name        text NOT NULL,
    industry    text,
    website     text,
    street      text,
    postal_code text,
    city        text,
    country     text,
    owner_id    uuid REFERENCES users (id),
    external_id text,
    deleted_at  timestamptz,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_accounts_tenant_external_id ON accounts (tenant_id, external_id) WHERE external_id IS NOT NULL;
CREATE INDEX idx_accounts_tenant_name ON accounts (tenant_id, name) WHERE deleted_at IS NULL;
CREATE INDEX idx_accounts_tenant_created ON accounts (tenant_id, created_at DESC, id DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_accounts_name_trgm ON accounts USING gin (name gin_trgm_ops);
CREATE TRIGGER trg_accounts_updated_at BEFORE UPDATE ON accounts
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE contacts (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        uuid NOT NULL REFERENCES tenants (id),
    account_id       uuid REFERENCES accounts (id),
    first_name       text,
    last_name        text NOT NULL,
    email            text,
    phone            text,
    position         text,
    gdpr_consent_at  timestamptz,
    external_id      text,
    deleted_at       timestamptz,
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_contacts_tenant_external_id ON contacts (tenant_id, external_id) WHERE external_id IS NOT NULL;
CREATE INDEX idx_contacts_tenant_account ON contacts (tenant_id, account_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_contacts_tenant_email ON contacts (tenant_id, email) WHERE email IS NOT NULL;
CREATE INDEX idx_contacts_tenant_created ON contacts (tenant_id, created_at DESC, id DESC) WHERE deleted_at IS NULL;
CREATE TRIGGER trg_contacts_updated_at BEFORE UPDATE ON contacts
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Leads referenzieren jetzt existierende Zieltabellen der Konvertierung
ALTER TABLE leads
    ADD CONSTRAINT fk_leads_converted_account FOREIGN KEY (converted_account_id) REFERENCES accounts (id),
    ADD CONSTRAINT fk_leads_converted_contact FOREIGN KEY (converted_contact_id) REFERENCES contacts (id);

-- RLS analog zu V1
DO $$
DECLARE
    t text;
BEGIN
    FOREACH t IN ARRAY ARRAY['accounts', 'contacts']
    LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
        EXECUTE format(
            'CREATE POLICY tenant_isolation ON %I FOR ALL '
            || 'USING (tenant_id = NULLIF(current_setting(''app.current_tenant'', true), '''')::uuid) '
            || 'WITH CHECK (tenant_id = NULLIF(current_setting(''app.current_tenant'', true), '''')::uuid)', t);
    END LOOP;
END $$;
