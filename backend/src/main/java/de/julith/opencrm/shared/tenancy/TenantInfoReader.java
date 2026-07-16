package de.julith.opencrm.shared.tenancy;

import java.util.NoSuchElementException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Leichtgewichtiger Lesezugriff auf Tenant-Stammwerte (Default-Währung, Settings)
 * für Fachmodule — vermeidet Modulzyklen mit dem tenant-Modul.
 * Läuft unter dem aktuellen Tenant-Kontext (RLS-Select-Policy auf tenants).
 */
@Component
public class TenantInfoReader {

    private final JdbcTemplate jdbcTemplate;

    public TenantInfoReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public String defaultCurrency() {
        var tenantId = TenantContext.get();
        if (tenantId == null) {
            throw new IllegalStateException("Kein Tenant-Kontext gesetzt");
        }
        var result = jdbcTemplate.queryForList(
                "SELECT default_currency FROM tenants WHERE id = ?", String.class, tenantId);
        if (result.isEmpty()) {
            throw new NoSuchElementException("Tenant " + tenantId + " nicht gefunden");
        }
        return result.getFirst().trim();
    }

    public String settingOrDefault(String key, String defaultValue) {
        var tenantId = TenantContext.get();
        if (tenantId == null) {
            return defaultValue;
        }
        var result = jdbcTemplate.queryForList(
                "SELECT settings ->> ? FROM tenants WHERE id = ?", String.class, key, tenantId);
        return result.isEmpty() || result.getFirst() == null ? defaultValue : result.getFirst();
    }
}
