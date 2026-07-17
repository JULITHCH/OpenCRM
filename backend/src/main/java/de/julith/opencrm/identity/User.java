package de.julith.opencrm.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "keycloak_id", nullable = false, unique = true)
    private String keycloakId;

    @Column(nullable = false)
    private String email;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    /** Höchstprivilegierte Realm-Rolle des Nutzers; wird bei jedem Login synchronisiert. */
    @Column(nullable = false)
    private String role;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected User() {
    }

    public User(UUID tenantId, String keycloakId, String email, String displayName, String role) {
        this.tenantId = tenantId;
        this.keycloakId = keycloakId;
        this.email = email;
        this.displayName = displayName;
        this.role = role;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getKeycloakId() {
        return keycloakId;
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getRole() {
        return role;
    }

    public boolean isActive() {
        return active;
    }

    public boolean syncFrom(String email, String displayName, String role) {
        boolean changed = false;
        if (email != null && !email.equals(this.email)) {
            this.email = email;
            changed = true;
        }
        if (displayName != null && !displayName.equals(this.displayName)) {
            this.displayName = displayName;
            changed = true;
        }
        if (role != null && !role.equals(this.role)) {
            this.role = role;
            changed = true;
        }
        return changed;
    }

    public void deactivate() {
        this.active = false;
    }
}
