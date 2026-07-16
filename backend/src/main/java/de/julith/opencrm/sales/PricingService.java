package de.julith.opencrm.sales;

import de.julith.opencrm.crmcore.Account;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Preisaufloesung laut docs/07 Abschnitt 2: gueltige Account-Preisliste vor Listenpreis;
 * der positionsbezogene Rabatt wird erst auf der Opportunity-Position angewendet.
 */
@Service
public class PricingService {

    private final PriceListRepository priceListRepository;
    private final PriceListItemRepository priceListItemRepository;

    public PricingService(PriceListRepository priceListRepository,
                          PriceListItemRepository priceListItemRepository) {
        this.priceListRepository = priceListRepository;
        this.priceListItemRepository = priceListItemRepository;
    }

    public BigDecimal resolveUnitPrice(Account account, Product product, LocalDate pricingDate) {
        if (account.getPriceListId() != null) {
            Optional<BigDecimal> priceListPrice = priceListRepository.findById(account.getPriceListId())
                    .filter(priceList -> priceList.isValidOn(pricingDate))
                    .flatMap(priceList -> priceListItemRepository
                            .findByPriceListIdAndProductId(priceList.getId(), product.getId()))
                    .map(PriceListItem::getUnitPrice);
            if (priceListPrice.isPresent()) {
                return priceListPrice.get();
            }
        }
        return product.getListPrice();
    }
}
