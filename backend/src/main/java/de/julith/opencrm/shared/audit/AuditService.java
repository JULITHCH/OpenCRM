package de.julith.opencrm.shared.audit;

import de.julith.opencrm.shared.tenancy.TenantContext;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Schreibt fachliche Audit-Einträge (docs/03 Abschnitt 8). Läuft in der umgebenden
 * Transaktion des Aufrufers — der Eintrag erscheint genau dann, wenn die Fachänderung committet.
 */
@Component
public class AuditService {

    private final JdbcTemplate jdbcTemplate;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    public AuditService(JdbcTemplate jdbcTemplate, com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public void record(String action, String entityType, UUID entityId, UUID actorId, Map<String, Object> diff) {
        UUID tenantId = TenantContext.get();
        if (tenantId == null) {
            return;
        }
        String diffJson;
        try {
            diffJson = diff == null ? null : objectMapper.writeValueAsString(diff);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            diffJson = null;
        }
        jdbcTemplate.update("""
                INSERT INTO audit_log (tenant_id, actor_id, entity_type, entity_id, action, diff)
                VALUES (?, ?, ?, ?, ?, ?::jsonb)
                """, tenantId, actorId, entityType, entityId, action, diffJson);
    }
}
