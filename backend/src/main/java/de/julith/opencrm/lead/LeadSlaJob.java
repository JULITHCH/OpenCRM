package de.julith.opencrm.lead;

import de.julith.opencrm.identity.NotificationWriter;
import de.julith.opencrm.shared.tenancy.TenantInfoReader;
import de.julith.opencrm.shared.tenancy.TenantScopedExecutor;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * SLA-Ueberwachung (docs/06 Abschnitt 5): zugewiesene Leads ohne erste Aktivitaet innerhalb
 * der konfigurierten Frist (tenants.settings sla_hours, Default 24, Kalenderstunden E-29)
 * loesen eine Benachrichtigung an den Verantwortlichen aus — genau einmal je Lead.
 */
@Component
public class LeadSlaJob {

    static final String NOTIFICATION_TYPE = "LEAD_SLA_BREACH";

    private static final Logger log = LoggerFactory.getLogger(LeadSlaJob.class);

    private final JdbcTemplate jdbcTemplate;
    private final TenantScopedExecutor tenantScopedExecutor;
    private final TenantInfoReader tenantInfoReader;
    private final NotificationWriter notificationWriter;

    public LeadSlaJob(JdbcTemplate jdbcTemplate, TenantScopedExecutor tenantScopedExecutor,
                      TenantInfoReader tenantInfoReader, NotificationWriter notificationWriter) {
        this.jdbcTemplate = jdbcTemplate;
        this.tenantScopedExecutor = tenantScopedExecutor;
        this.tenantInfoReader = tenantInfoReader;
        this.notificationWriter = notificationWriter;
    }

    @Scheduled(fixedDelayString = "PT15M", initialDelayString = "PT2M")
    @SchedulerLock(name = "lead_sla_check", lockAtMostFor = "14m")
    public void checkAllTenants() {
        List<UUID> tenants = jdbcTemplate.query("SELECT id FROM tenants WHERE status = 'ACTIVE'",
                (rs, rowNum) -> rs.getObject(1, UUID.class));
        for (UUID tenantId : tenants) {
            try {
                tenantScopedExecutor.runAs(tenantId, this::checkCurrentTenant);
            } catch (Exception e) {
                log.error("SLA-Pruefung fuer Tenant {} fehlgeschlagen", tenantId, e);
            }
        }
    }

    /** Läuft unter gesetztem Tenant-Kontext (auch aus Tests direkt aufrufbar). */
    public void checkCurrentTenant() {
        int slaHours = Integer.parseInt(tenantInfoReader.settingOrDefault("sla_hours", "24"));
        List<Map<String, Object>> breached = jdbcTemplate.queryForList("""
                SELECT l.id AS lead_id, l.owner_id, l.title
                FROM leads l
                JOIN LATERAL (
                    SELECT max(assigned_at) AS assigned_at FROM lead_assignments la WHERE la.lead_id = l.id
                ) la ON true
                WHERE l.status = 'ASSIGNED'
                  AND l.deleted_at IS NULL
                  AND l.owner_id IS NOT NULL
                  AND la.assigned_at < now() - make_interval(hours => ?)
                  AND NOT EXISTS (
                      SELECT 1 FROM activities a
                      WHERE a.lead_id = l.id AND a.created_at > la.assigned_at
                  )
                """, slaHours);
        for (Map<String, Object> row : breached) {
            UUID leadId = (UUID) row.get("lead_id");
            UUID ownerId = (UUID) row.get("owner_id");
            if (notificationWriter.alreadyNotifiedForLead(NOTIFICATION_TYPE, leadId)) {
                continue;
            }
            notificationWriter.notify(ownerId, NOTIFICATION_TYPE, Map.of(
                    "leadId", leadId.toString(),
                    "title", row.get("title") != null ? row.get("title").toString() : "",
                    "slaHours", slaHours));
        }
    }
}
