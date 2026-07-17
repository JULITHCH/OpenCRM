package de.julith.opencrm.tenant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "tenants")
public class Tenant {

    public enum Status { ACTIVE, SUSPENDED, OFFBOARDING }

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.ACTIVE;

    @Column(nullable = false)
    private String plan = "standard";

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "default_currency", nullable = false, length = 3)
    private String defaultCurrency = "EUR";

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected Tenant() {
    }

    public Tenant(UUID id, String name, String slug) {
        this.id = id;
        this.name = name;
        this.slug = slug;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getSlug() {
        return slug;
    }

    public Status getStatus() {
        return status;
    }

    public String getPlan() {
        return plan;
    }

    public String getDefaultCurrency() {
        return defaultCurrency;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
