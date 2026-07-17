package de.julith.opencrm.sales;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PriceListItemRepository extends JpaRepository<PriceListItem, UUID> {

    List<PriceListItem> findByPriceListId(UUID priceListId);

    Optional<PriceListItem> findByPriceListIdAndProductId(UUID priceListId, UUID productId);
}
