package de.julith.opencrm.tenant;

import de.julith.opencrm.shared.tenancy.TenantContext;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Mandanten-Self-Service (E-12): liest und pflegt tenants.settings per Merge.
 * Nur bekannte Schluessel sind zugelassen, damit sich keine wilden Konfigurationen ansammeln.
 */
@RestController
@RequestMapping("/api/v1/tenant-settings")
@PreAuthorize("hasRole('tenant-admin')")
public class TenantSettingsController {

    static final Set<String> ALLOWED_KEYS = Set.of(
            "lead_claim_enabled", "sla_hours", "duplicate_threshold_candidate", "duplicate_threshold_strong",
            "lead_retention_months", "contact_deletion_days", "audit_retention_months", "default_team_id",
            "offboarding_grace_days");

    private final JdbcTemplate jdbcTemplate;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    public TenantSettingsController(JdbcTemplate jdbcTemplate,
                                    com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public Map<String, Object> get() {
        return jdbcTemplate.query(
                "SELECT name, slug, plan, default_currency, settings::text FROM tenants WHERE id = ?",
                rs -> {
                    if (!rs.next()) {
                        throw new NoSuchElementException("Tenant nicht gefunden");
                    }
                    Map<String, Object> result = new java.util.LinkedHashMap<>();
                    result.put("name", rs.getString(1));
                    result.put("slug", rs.getString(2));
                    result.put("plan", rs.getString(3));
                    result.put("defaultCurrency", rs.getString(4).trim());
                    result.put("settings", parse(rs.getString(5)));
                    return result;
                }, TenantContext.get());
    }

    @PatchMapping
    @Transactional
    public Map<String, Object> patch(@RequestBody Map<String, Object> settings) {
        for (String key : settings.keySet()) {
            if (!ALLOWED_KEYS.contains(key)) {
                throw new IllegalArgumentException("Unbekannter Einstellungs-Schluessel: " + key);
            }
        }
        String json;
        try {
            json = objectMapper.writeValueAsString(settings);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalArgumentException("Einstellungen nicht serialisierbar", e);
        }
        // Merge-Semantik: uebergebene Schluessel ueberschreiben, andere bleiben; null entfernt den Schluessel
        jdbcTemplate.update("UPDATE tenants SET settings = settings || ?::jsonb WHERE id = ?",
                json, TenantContext.get());
        jdbcTemplate.update("UPDATE tenants SET settings = settings - ? WHERE id = ?",
                settings.entrySet().stream().filter(e -> e.getValue() == null).map(Map.Entry::getKey)
                        .toArray(String[]::new), TenantContext.get());
        return get();
    }

    private Map<String, Object> parse(String json) {
        try {
            return objectMapper.readValue(json,
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                    });
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            return Map.of();
        }
    }
}
