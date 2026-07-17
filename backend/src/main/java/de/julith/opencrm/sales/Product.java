package de.julith.opencrm.sales;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "products")
public class Product {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String sku;

    @Column(nullable = false)
    private String name;

    @Column
    private String description;

    @Column
    private String category;

    @Column
    private String unit;

    @Column(name = "list_price", nullable = false)
    private BigDecimal listPrice;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "tax_rate")
    private BigDecimal taxRate;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "external_id")
    private String externalId;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected Product() {
    }

    public Product(UUID tenantId, String sku, String name, BigDecimal listPrice, String currency) {
        this.tenantId = tenantId;
        this.sku = sku;
        this.name = name;
        this.listPrice = listPrice;
        this.currency = currency;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getSku() {
        return sku;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getCategory() {
        return category;
    }

    public String getUnit() {
        return unit;
    }

    public BigDecimal getListPrice() {
        return listPrice;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getTaxRate() {
        return taxRate;
    }

    public boolean isActive() {
        return active;
    }

    public String getExternalId() {
        return externalId;
    }

    public OffsetDateTime getDeletedAt() {
        return deletedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public void setListPrice(BigDecimal listPrice) {
        this.listPrice = listPrice;
    }

    public void setTaxRate(BigDecimal taxRate) {
        this.taxRate = taxRate;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public void softDelete() {
        this.deletedAt = OffsetDateTime.now();
    }
}
