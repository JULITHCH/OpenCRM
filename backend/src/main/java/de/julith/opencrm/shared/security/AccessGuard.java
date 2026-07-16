package de.julith.opencrm.shared.security;

import java.util.Optional;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Durchsetzung des Owner-Scopes innerhalb eines Mandanten (docs/05/06/10): ein reiner
 * sales-rep darf nur Datensätze ändern, deren owner_id ihm gehört. RLS trennt nur Mandanten,
 * nicht Nutzer — deshalb diese zusätzliche Schicht im Service-Layer.
 */
@Component
public class AccessGuard {

    private final CallerContext callerContext;

    public AccessGuard(CallerContext callerContext) {
        this.callerContext = callerContext;
    }

    /**
     * Wirft 403, wenn ein beschränkter Aufrufer einen fremden (oder besitzerlosen) Datensatz ändern will.
     * Manager/Admins passieren immer.
     */
    public void requireCanMutate(UUID ownerId) {
        if (!callerContext.restrictedToOwn()) {
            return;
        }
        UUID self = callerContext.currentUserId().orElse(null);
        if (self == null || !self.equals(ownerId)) {
            throw new AccessDeniedException("Nur eigene Datensaetze duerfen geaendert werden");
        }
    }

    /** Owner-Filter für Listen: leer = uneingeschränkt (Manager/Admin), sonst genau der eigene Nutzer. */
    public Optional<UUID> ownerFilter() {
        if (!callerContext.restrictedToOwn()) {
            return Optional.empty();
        }
        return Optional.of(callerContext.currentUserId().orElse(new UUID(0, 0)));
    }
}
