package de.julith.opencrm.lead;

import de.julith.opencrm.shared.tenancy.TenantContext;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Round-Robin je Team (docs/06 Abschnitt 3.2): persistenter Zeiger in round_robin_pointers,
 * Sperrung via SELECT ... FOR UPDATE gegen parallele Zuweisungen, inaktive Nutzer werden
 * übersprungen. Muss innerhalb einer Transaktion mit gesetztem Tenant-Kontext laufen.
 */
@Component
public class RoundRobinAssigner {

    private final JdbcTemplate jdbcTemplate;

    public RoundRobinAssigner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<UUID> nextAssignee(UUID teamId) {
        List<UUID> members = jdbcTemplate.query("""
                SELECT u.id FROM team_members tm
                JOIN users u ON u.id = tm.user_id
                WHERE tm.team_id = ? AND u.active
                ORDER BY u.id
                """, (rs, rowNum) -> rs.getObject(1, UUID.class), teamId);
        if (members.isEmpty()) {
            return Optional.empty();
        }

        jdbcTemplate.update("""
                INSERT INTO round_robin_pointers (tenant_id, team_id)
                VALUES (?, ?) ON CONFLICT (tenant_id, team_id) DO NOTHING
                """, TenantContext.get(), teamId);
        UUID lastUserId = jdbcTemplate.query(
                "SELECT last_user_id FROM round_robin_pointers WHERE team_id = ? FOR UPDATE",
                rs -> rs.next() ? rs.getObject(1, UUID.class) : null, teamId);

        UUID next = members.getFirst();
        if (lastUserId != null) {
            for (UUID member : members) {
                if (member.toString().compareTo(lastUserId.toString()) > 0) {
                    next = member;
                    break;
                }
            }
        }

        jdbcTemplate.update(
                "UPDATE round_robin_pointers SET last_user_id = ?, updated_at = now() WHERE team_id = ?",
                next, teamId);
        return Optional.of(next);
    }
}
