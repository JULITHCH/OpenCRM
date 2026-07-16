package de.julith.opencrm.shared.audit;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audit-log")
@PreAuthorize("hasAnyRole('tenant-admin', 'read-only')")
public class AuditController {

    private final JdbcTemplate jdbcTemplate;

    public AuditController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public record AuditEntry(UUID id, UUID actorId, String entityType, UUID entityId, String action,
                             String diff, String occurredAt) {
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<AuditEntry> list(@RequestParam(required = false) String entityType,
                                 @RequestParam(required = false, defaultValue = "100") int limit) {
        int pageSize = Math.min(Math.max(limit, 1), 500);
        List<Object> params = new java.util.ArrayList<>();
        String where = "";
        if (entityType != null && !entityType.isBlank()) {
            where = "WHERE entity_type = ?";
            params.add(entityType);
        }
        params.add(pageSize);
        return jdbcTemplate.query("""
                SELECT id, actor_id, entity_type, entity_id, action, diff::text, occurred_at
                FROM audit_log %s ORDER BY occurred_at DESC LIMIT ?
                """.formatted(where),
                (rs, rowNum) -> new AuditEntry(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                        rs.getString(3), rs.getObject(4, UUID.class), rs.getString(5), rs.getString(6),
                        rs.getObject(7, java.time.OffsetDateTime.class).toString()),
                params.toArray());
    }
}
