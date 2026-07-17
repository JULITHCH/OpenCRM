package de.julith.opencrm.sales;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "price_list_items")
public class PriceListItem {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "price_list_id", nullable = false)
    private UUID priceListId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "unit_price", nullable = false)
    private BigDecimal unitPrice;

    protected PriceListItem() {
    }

    public PriceListItem(UUID priceListId, UUID productId, BigDecimal unitPrice) {
        this.priceListId = priceListId;
        this.productId = productId;
        this.unitPrice = unitPrice;
    }

    public UUID getId() {
        return id;
    }

    public UUID getPriceListId() {
        return priceListId;
    }

    public UUID getProductId() {
        return productId;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }
}
