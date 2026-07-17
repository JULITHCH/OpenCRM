package de.julith.opencrm.identity;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * JIT-Provisionierung: legt beim ersten authentifizierten Request eines Nutzers die users-Zeile an
 * und synchronisiert E-Mail/Anzeigename/Rolle bei Änderungen (docs/05, Abschnitt 7).
 * Ein kurzlebiger In-Memory-Cache verhindert einen DB-Roundtrip pro Request.
 */
@Service
public class UserProvisioningService {

    /** Rangfolge für die Ableitung der Hauptrolle aus den Realm-Rollen. */
    private static final List<String> ROLE_PRIORITY =
            List.of("platform-admin", "tenant-admin", "sales-manager", "sales-rep", "read-only");

    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private final UserRepository userRepository;
    private final Map<String, Instant> recentlySynced = new ConcurrentHashMap<>();

    public UserProvisioningService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void ensureUser(Jwt jwt, UUID tenantId) {
        String keycloakId = jwt.getSubject();
        Instant synced = recentlySynced.get(keycloakId);
        if (synced != null && synced.isAfter(Instant.now().minus(CACHE_TTL))) {
            return;
        }

        String email = jwt.getClaimAsString("email");
        String displayName = jwt.getClaimAsString("name") != null
                ? jwt.getClaimAsString("name")
                : jwt.getClaimAsString("preferred_username");
        String role = mainRole(jwt);

        userRepository.findByKeycloakId(keycloakId).ifPresentOrElse(
                user -> user.syncFrom(email, displayName, role),
                () -> userRepository.save(new User(tenantId, keycloakId,
                        email != null ? email : keycloakId,
                        displayName != null ? displayName : "unbekannt",
                        role)));
        recentlySynced.put(keycloakId, Instant.now());
    }

    @SuppressWarnings("unchecked")
    private static String mainRole(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        List<String> roles = realmAccess != null && realmAccess.get("roles") instanceof List<?> list
                ? list.stream().map(Object::toString).toList()
                : List.of();
        return ROLE_PRIORITY.stream().filter(roles::contains).findFirst().orElse("read-only");
    }
}
