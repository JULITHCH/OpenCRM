package de.julith.opencrm.crmcore;

import de.julith.opencrm.shared.audit.AuditService;
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
 * DSGVO-Anonymisierung (E-02): personenbezogene Felder werden unwiderruflich
 * ueberschrieben, Datensaetze und Kennzahlen bleiben erhalten.
 * Fristen je Tenant konfigurierbar: lead_retention_months (Default 12, ab
 * Disqualifikation) und contact_deletion_days (Default 30, ab Soft Delete).
 */
@Component
public class GdprAnonymizationJob {

    private static final Logger log = LoggerFactory.getLogger(GdprAnonymizationJob.class);

    private final JdbcTemplate jdbcTemplate;
    private final TenantScopedExecutor tenantScopedExecutor;
    private final TenantInfoReader tenantInfoReader;
    private final AuditService auditService;

    public GdprAnonymizationJob(JdbcTemplate jdbcTemplate, TenantScopedExecutor tenantScopedExecutor,
                                TenantInfoReader tenantInfoReader, AuditService auditService) {
        this.jdbcTemplate = jdbcTemplate;
        this.tenantScopedExecutor = tenantScopedExecutor;
        this.tenantInfoReader = tenantInfoReader;
        this.auditService = auditService;
    }

    @Scheduled(cron = "0 30 2 * * *")
    @SchedulerLock(name = "gdpr_anonymization", lockAtMostFor = "1h")
    public void anonymizeAllTenants() {
        List<UUID> tenants = jdbcTemplate.query("SELECT id FROM tenants WHERE status = 'ACTIVE'",
                (rs, rowNum) -> rs.getObject(1, UUID.class));
        for (UUID tenantId : tenants) {
            try {
                tenantScopedExecutor.runAs(tenantId, this::anonymizeCurrentTenant);
            } catch (Exception e) {
                log.error("DSGVO-Anonymisierung fuer Tenant {} fehlgeschlagen", tenantId, e);
            }
        }
    }

    /** Läuft unter gesetztem Tenant-Kontext (auch aus Tests direkt aufrufbar). */
    public void anonymizeCurrentTenant() {
        int leadRetentionMonths = Integer.parseInt(
                tenantInfoReader.settingOrDefault("lead_retention_months", "12"));
        int contactDeletionDays = Integer.parseInt(
                tenantInfoReader.settingOrDefault("contact_deletion_days", "30"));

        int leads = jdbcTemplate.update("""
                UPDATE leads SET
                    first_name = NULL, last_name = '[anonymisiert]', email = NULL, phone = NULL,
                    title = '[anonymisiert]', custom = '{}'::jsonb
                WHERE status = 'DISQUALIFIED'
                  AND disqualified_at < now() - make_interval(months => ?)
                  AND (email IS NOT NULL OR first_name IS NOT NULL OR phone IS NOT NULL)
                """, leadRetentionMonths);

        int contacts = jdbcTemplate.update("""
                UPDATE contacts SET
                    first_name = NULL, last_name = '[anonymisiert]', email = NULL, phone = NULL, position = NULL
                WHERE deleted_at IS NOT NULL
                  AND deleted_at < now() - make_interval(days => ?)
                  AND (email IS NOT NULL OR first_name IS NOT NULL OR phone IS NOT NULL)
                """, contactDeletionDays);

        if (leads > 0 || contacts > 0) {
            auditService.record("DELETE", "GDPR_ANONYMIZATION", null, null,
                    Map.of("anonymizedLeads", leads, "anonymizedContacts", contacts));
            log.info("DSGVO-Anonymisierung: {} Leads, {} Kontakte", leads, contacts);
        }
    }
}
