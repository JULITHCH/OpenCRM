package de.julith.opencrm.sales;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "pipelines")
public class Pipeline {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String name;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    protected Pipeline() {
    }

    public Pipeline(UUID tenantId, String name, boolean isDefault) {
        this.tenantId = tenantId;
        this.name = name;
        this.isDefault = isDefault;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getName() {
        return name;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public void setName(String name) {
        this.name = name;
    }
}
