-- M3: ShedLock-Tabelle (E-43) und materialisierte Sicht fuer die Dashboard-KPIs
-- (docs/09-dashboard-und-reporting.md; Kern-Dimensionen day/tenant/owner — die
-- Erweiterung um team_id/pipeline_id/product_category folgt bei Bedarf additiv).

CREATE TABLE shedlock (
    name       text PRIMARY KEY,
    lock_until timestamptz NOT NULL,
    locked_at  timestamptz NOT NULL,
    locked_by  text NOT NULL
);

CREATE MATERIALIZED VIEW mv_sales_kpis_daily AS
SELECT day,
       tenant_id,
       owner_id,
       SUM(won_amount)       AS won_amount,
       SUM(won_count)        AS won_count,
       SUM(lost_count)       AS lost_count,
       SUM(new_leads)        AS new_leads,
       SUM(converted_leads)  AS converted_leads,
       SUM(activities_count) AS activities_count
FROM (
    SELECT tenant_id, owner_id, (won_at AT TIME ZONE 'UTC')::date AS day,
           amount AS won_amount, 1 AS won_count, 0 AS lost_count,
           0 AS new_leads, 0 AS converted_leads, 0 AS activities_count
    FROM opportunities WHERE status = 'WON' AND won_at IS NOT NULL
    UNION ALL
    SELECT tenant_id, owner_id, (lost_at AT TIME ZONE 'UTC')::date,
           0, 0, 1, 0, 0, 0
    FROM opportunities WHERE status = 'LOST' AND lost_at IS NOT NULL
    UNION ALL
    SELECT tenant_id, owner_id, (created_at AT TIME ZONE 'UTC')::date,
           0, 0, 0, 1, 0, 0
    FROM leads WHERE deleted_at IS NULL
    UNION ALL
    SELECT tenant_id, owner_id, (converted_at AT TIME ZONE 'UTC')::date,
           0, 0, 0, 0, 1, 0
    FROM leads WHERE converted_at IS NOT NULL
    UNION ALL
    SELECT tenant_id, owner_id, (created_at AT TIME ZONE 'UTC')::date,
           0, 0, 0, 0, 0, 1
    FROM activities WHERE deleted_at IS NULL
) facts
GROUP BY day, tenant_id, owner_id;

-- Voraussetzung fuer REFRESH MATERIALIZED VIEW CONCURRENTLY
CREATE UNIQUE INDEX ux_mv_sales_kpis_daily ON mv_sales_kpis_daily (day, tenant_id, owner_id)
    NULLS NOT DISTINCT;
CREATE INDEX idx_mv_sales_kpis_tenant_day ON mv_sales_kpis_daily (tenant_id, day);

-- RLS wirkt nicht auf materialisierte Sichten: Zugriff NUR ueber Backend-Queries,
-- die tenant_id explizit filtern (docs/09 Abschnitt 5). Der Refresh (REFRESH ... CONCURRENTLY)
-- verlangt Eigentuemerschaft — die Sicht gehoert daher der App-Rolle
-- (CREATE-Recht dafuer kommt aus infra/postgres/init/01-roles.sql).
ALTER MATERIALIZED VIEW mv_sales_kpis_daily OWNER TO opencrm_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON shedlock TO opencrm_app;
