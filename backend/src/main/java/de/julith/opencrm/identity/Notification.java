package de.julith.opencrm.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String type;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> payload = new java.util.HashMap<>();

    @Column(name = "read_at")
    private OffsetDateTime readAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected Notification() {
    }

    public Notification(UUID tenantId, UUID userId, String type, Map<String, Object> payload) {
        this.tenantId = tenantId;
        this.userId = userId;
        this.type = type;
        this.payload = payload != null ? payload : new java.util.HashMap<>();
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getType() {
        return type;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public OffsetDateTime getReadAt() {
        return readAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void markRead() {
        if (this.readAt == null) {
            this.readAt = OffsetDateTime.now();
        }
    }
}
