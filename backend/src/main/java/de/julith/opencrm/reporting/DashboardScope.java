package de.julith.opencrm.reporting;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Sichtbarkeits-Scope laut docs/09 Abschnitt 4 — durchgesetzt im Backend, nie im Frontend:
 * sales-rep sieht nur sich selbst, sales-manager die Mitglieder seiner geführten Teams,
 * tenant-admin und read-only alles im Mandanten.
 */
@Component
public class DashboardScope {

    /** Leere ownerIds-Liste = kein Owner-Filter (alles im Mandanten sichtbar). */
    public record Scope(List<UUID> ownerIds, boolean leaderboardAllowed) {
        public boolean unrestricted() {
            return ownerIds.isEmpty();
        }
    }

    private final JdbcTemplate jdbcTemplate;

    public DashboardScope(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Scope resolve(JwtAuthenticationToken auth) {
        List<String> roles = auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
        if (roles.contains("ROLE_tenant-admin") || roles.contains("ROLE_read-only")
                || roles.contains("ROLE_platform-admin")) {
            return new Scope(List.of(), true);
        }
        UUID selfId = jdbcTemplate.query("SELECT id FROM users WHERE keycloak_id = ?",
                rs -> rs.next() ? rs.getObject(1, UUID.class) : null, auth.getToken().getSubject());
        if (selfId == null) {
            return new Scope(List.of(new UUID(0, 0)), false);
        }
        if (roles.contains("ROLE_sales-manager")) {
            List<UUID> teamMembers = jdbcTemplate.query("""
                    SELECT DISTINCT tm.user_id FROM team_members tm
                    WHERE tm.team_id IN (SELECT team_id FROM team_members WHERE user_id = ? AND is_lead)
                    """, (rs, rowNum) -> rs.getObject(1, UUID.class), selfId);
            List<UUID> scope = new java.util.ArrayList<>(teamMembers);
            if (!scope.contains(selfId)) {
                scope.add(selfId);
            }
            return new Scope(List.copyOf(scope), true);
        }
        return new Scope(List.of(selfId), false);
    }
}
