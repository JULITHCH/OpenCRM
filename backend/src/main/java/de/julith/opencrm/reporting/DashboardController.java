package de.julith.opencrm.reporting;

import de.julith.opencrm.shared.tenancy.TenantContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dashboard-Endpunkte (docs/09): KPIs und Funnel live auf den Basistabellen,
 * Zeitreihe aus mv_sales_kpis_daily plus Live-Anteil fuer den laufenden Tag.
 * Alle Queries filtern tenant_id explizit — die MV unterliegt keiner RLS.
 */
@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final NamedParameterJdbcTemplate namedJdbc;
    private final DashboardScope dashboardScope;

    public DashboardController(JdbcTemplate jdbcTemplate, DashboardScope dashboardScope) {
        this.namedJdbc = new NamedParameterJdbcTemplate(jdbcTemplate);
        this.dashboardScope = dashboardScope;
    }

    @GetMapping("/kpis")
    @Transactional(readOnly = true)
    public Map<String, Object> kpis(@RequestParam LocalDate from, @RequestParam LocalDate to,
                                    JwtAuthenticationToken auth) {
        DashboardScope.Scope scope = dashboardScope.resolve(auth);
        MapSqlParameterSource params = baseParams(scope, from, to);
        String ownerFilter = ownerFilter(scope, "owner_id");

        Map<String, Object> won = namedJdbc.queryForMap("""
                SELECT COALESCE(SUM(amount), 0) AS revenue, COUNT(*) AS won_count,
                       COALESCE(AVG(EXTRACT(EPOCH FROM (won_at - created_at)) / 86400.0), 0) AS avg_cycle_days
                FROM opportunities
                WHERE tenant_id = :tenantId AND status = 'WON'
                  AND won_at >= :fromTs AND won_at < :toTs %s
                """.formatted(ownerFilter), params);
        Long lostCount = namedJdbc.queryForObject("""
                SELECT COUNT(*) FROM opportunities
                WHERE tenant_id = :tenantId AND status = 'LOST'
                  AND lost_at >= :fromTs AND lost_at < :toTs %s
                """.formatted(ownerFilter), params, Long.class);
        Map<String, Object> pipeline = namedJdbc.queryForMap("""
                SELECT COALESCE(SUM(o.amount), 0) AS open_amount,
                       COALESCE(SUM(o.amount * ps.probability / 100), 0) AS weighted_amount
                FROM opportunities o JOIN pipeline_stages ps ON ps.id = o.stage_id
                WHERE o.tenant_id = :tenantId AND o.status = 'OPEN' AND o.deleted_at IS NULL %s
                """.formatted(ownerFilter(scope, "o.owner_id")), params);
        Map<String, Object> leads = namedJdbc.queryForMap("""
                SELECT COUNT(*) FILTER (WHERE converted_at >= :fromTs AND converted_at < :toTs) AS converted,
                       COUNT(*) FILTER (WHERE disqualified_at >= :fromTs AND disqualified_at < :toTs) AS disqualified
                FROM leads WHERE tenant_id = :tenantId %s
                """.formatted(ownerFilter), params);
        Long activities = namedJdbc.queryForObject("""
                SELECT COUNT(*) FROM activities
                WHERE tenant_id = :tenantId AND deleted_at IS NULL
                  AND created_at >= :fromTs AND created_at < :toTs %s
                """.formatted(ownerFilter), params, Long.class);

        long wonCount = ((Number) won.get("won_count")).longValue();
        long converted = ((Number) leads.get("converted")).longValue();
        long disqualified = ((Number) leads.get("disqualified")).longValue();
        return Map.of(
                "revenue", won.get("revenue"),
                "wonCount", wonCount,
                "lostCount", lostCount,
                "winRate", rate(wonCount, wonCount + lostCount),
                "leadConversionRate", rate(converted, converted + disqualified),
                "avgSalesCycleDays", ((Number) won.get("avg_cycle_days")).doubleValue(),
                "openPipeline", pipeline.get("open_amount"),
                "weightedPipeline", pipeline.get("weighted_amount"),
                "activitiesCount", activities);
    }

    @GetMapping("/pipeline-funnel")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> pipelineFunnel(JwtAuthenticationToken auth) {
        DashboardScope.Scope scope = dashboardScope.resolve(auth);
        MapSqlParameterSource params = baseParams(scope, null, null);
        return namedJdbc.queryForList("""
                SELECT ps.id AS stage_id, ps.name, ps.sort_order,
                       COUNT(o.id) AS count, COALESCE(SUM(o.amount), 0) AS amount,
                       COALESCE(SUM(o.amount * ps.probability / 100), 0) AS weighted_amount
                FROM pipeline_stages ps
                JOIN pipelines p ON p.id = ps.pipeline_id AND p.tenant_id = :tenantId
                LEFT JOIN opportunities o ON o.stage_id = ps.id AND o.status = 'OPEN'
                     AND o.deleted_at IS NULL %s
                WHERE NOT ps.is_won AND NOT ps.is_lost
                GROUP BY ps.id, ps.name, ps.sort_order
                ORDER BY ps.sort_order
                """.formatted(ownerFilter(scope, "o.owner_id")), params);
    }

    @GetMapping("/revenue-timeseries")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> revenueTimeseries(@RequestParam LocalDate from, @RequestParam LocalDate to,
                                                       JwtAuthenticationToken auth) {
        DashboardScope.Scope scope = dashboardScope.resolve(auth);
        MapSqlParameterSource params = baseParams(scope, from, to);
        // Historie aus der MV, laufender Tag live (docs/09 Abschnitt 5)
        return namedJdbc.queryForList("""
                SELECT day, SUM(won_amount) AS revenue, SUM(won_count) AS won_count FROM (
                    SELECT day, won_amount, won_count FROM mv_sales_kpis_daily
                    WHERE tenant_id = :tenantId AND day >= :fromDay AND day < :toDay
                      AND day < (now() AT TIME ZONE 'UTC')::date %s
                    UNION ALL
                    SELECT (won_at AT TIME ZONE 'UTC')::date, amount, 1 FROM opportunities
                    WHERE tenant_id = :tenantId AND status = 'WON'
                      AND (won_at AT TIME ZONE 'UTC')::date = (now() AT TIME ZONE 'UTC')::date
                      AND (won_at AT TIME ZONE 'UTC')::date >= :fromDay
                      AND (won_at AT TIME ZONE 'UTC')::date < :toDay %s
                ) t GROUP BY day ORDER BY day
                """.formatted(ownerFilter(scope, "owner_id"), ownerFilter(scope, "owner_id")), params);
    }

    @GetMapping("/leaderboard")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> leaderboard(@RequestParam LocalDate from, @RequestParam LocalDate to,
                                                 JwtAuthenticationToken auth) {
        DashboardScope.Scope scope = dashboardScope.resolve(auth);
        if (!scope.leaderboardAllowed()) {
            // sales-rep sieht keine Einzelwerte anderer Personen (E-75/docs 09 Abschnitt 4)
            throw new AccessDeniedException("Leaderboard ist Managern und Admins vorbehalten");
        }
        MapSqlParameterSource params = baseParams(scope, from, to);
        return namedJdbc.queryForList("""
                SELECT u.id AS user_id, u.display_name,
                       COALESCE(SUM(o.amount), 0) AS revenue, COUNT(o.id) AS won_count
                FROM users u
                LEFT JOIN opportunities o ON o.owner_id = u.id AND o.tenant_id = :tenantId
                     AND o.status = 'WON' AND o.won_at >= :fromTs AND o.won_at < :toTs
                WHERE u.tenant_id = :tenantId AND u.active %s
                GROUP BY u.id, u.display_name
                ORDER BY revenue DESC, u.display_name
                """.formatted(ownerFilter(scope, "u.id")), params);
    }

    private MapSqlParameterSource baseParams(DashboardScope.Scope scope, LocalDate from, LocalDate to) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", TenantContext.get());
        if (from != null && to != null) {
            params.addValue("fromDay", from)
                    .addValue("toDay", to.plusDays(1))
                    .addValue("fromTs", from.atStartOfDay(java.time.ZoneOffset.UTC).toOffsetDateTime())
                    .addValue("toTs", to.plusDays(1).atStartOfDay(java.time.ZoneOffset.UTC).toOffsetDateTime());
        }
        if (!scope.unrestricted()) {
            params.addValue("ownerIds", scope.ownerIds());
        }
        return params;
    }

    private static String ownerFilter(DashboardScope.Scope scope, String column) {
        return scope.unrestricted() ? "" : "AND " + column + " IN (:ownerIds)";
    }

    private static double rate(long numerator, long denominator) {
        return denominator == 0 ? 0.0 : (double) numerator / denominator;
    }
}
