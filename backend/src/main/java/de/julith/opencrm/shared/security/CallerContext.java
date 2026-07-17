package de.julith.opencrm.shared.security;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Löst den aktuellen Aufrufer aus dem SecurityContext auf — ohne das JWT durch alle
 * Service-Signaturen zu reichen. Liefert die interne users.id (kurz gecacht) und ob der
 * Aufrufer auf eigene Datensätze beschränkt ist (Rolle sales-rep; docs/05 Abschnitt 11).
 */
@Component
public class CallerContext {

    private static final Duration CACHE_TTL = Duration.ofMinutes(2);

    private final JdbcTemplate jdbcTemplate;
    private final Map<String, CachedId> cache = new ConcurrentHashMap<>();

    private record CachedId(UUID userId, Instant at) {
    }

    public CallerContext(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<UUID> currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof JwtAuthenticationToken jwt)) {
            return Optional.empty();
        }
        String subject = jwt.getToken().getSubject();
        CachedId cached = cache.get(subject);
        if (cached != null && cached.at().isAfter(Instant.now().minus(CACHE_TTL))) {
            return Optional.ofNullable(cached.userId());
        }
        UUID userId = jdbcTemplate.query("SELECT id FROM users WHERE keycloak_id = ?",
                rs -> rs.next() ? rs.getObject(1, UUID.class) : null, subject);
        cache.put(subject, new CachedId(userId, Instant.now()));
        return Optional.ofNullable(userId);
    }

    /** true, wenn der Aufrufer NUR eigene Datensätze sehen/ändern darf (reiner sales-rep). */
    public boolean restrictedToOwn() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return true;
        }
        boolean hasElevated = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(r -> r.equals("ROLE_tenant-admin") || r.equals("ROLE_sales-manager")
                        || r.equals("ROLE_read-only") || r.equals("ROLE_platform-admin"));
        return !hasElevated;
    }
}
