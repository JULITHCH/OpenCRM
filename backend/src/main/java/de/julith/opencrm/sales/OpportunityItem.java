package de.julith.opencrm.sales;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Entity
@Table(name = "opportunity_items")
public class OpportunityItem {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "opportunity_id", nullable = false)
    private UUID opportunityId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(nullable = false)
    private BigDecimal quantity;

    /** Preis-Snapshot zum Zeitpunkt der Positionsanlage (docs/07, Snapshot-Prinzip). */
    @Column(name = "unit_price", nullable = false)
    private BigDecimal unitPrice;

    @Column(name = "discount_pct", nullable = false)
    private BigDecimal discountPct = BigDecimal.ZERO;

    @Column(nullable = false)
    private int position;

    protected OpportunityItem() {
    }

    public OpportunityItem(UUID tenantId, UUID opportunityId, UUID productId, BigDecimal quantity,
                           BigDecimal unitPrice, BigDecimal discountPct, int position) {
        this.tenantId = tenantId;
        this.opportunityId = opportunityId;
        this.productId = productId;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.discountPct = discountPct != null ? discountPct : BigDecimal.ZERO;
        this.position = position;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOpportunityId() {
        return opportunityId;
    }

    public UUID getProductId() {
        return productId;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public BigDecimal getDiscountPct() {
        return discountPct;
    }

    public int getPosition() {
        return position;
    }

    public void update(BigDecimal quantity, BigDecimal unitPrice, BigDecimal discountPct) {
        if (quantity != null) {
            this.quantity = quantity;
        }
        if (unitPrice != null) {
            this.unitPrice = unitPrice;
        }
        if (discountPct != null) {
            this.discountPct = discountPct;
        }
    }

    /** Zeilensumme: Menge x Einzelpreis x (1 - Rabatt), kaufmännisch auf 2 Stellen gerundet. */
    public BigDecimal lineAmount() {
        BigDecimal factor = BigDecimal.ONE.subtract(
                discountPct.divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP));
        return quantity.multiply(unitPrice).multiply(factor).setScale(2, RoundingMode.HALF_UP);
    }
}
