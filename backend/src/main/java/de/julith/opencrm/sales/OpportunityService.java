package de.julith.opencrm.sales;

import de.julith.opencrm.crmcore.Account;
import de.julith.opencrm.crmcore.AccountRepository;
import de.julith.opencrm.shared.tenancy.TenantContext;
import de.julith.opencrm.shared.tenancy.TenantInfoReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OpportunityService {

    private final OpportunityRepository opportunityRepository;
    private final OpportunityItemRepository opportunityItemRepository;
    private final PipelineRepository pipelineRepository;
    private final PipelineStageRepository pipelineStageRepository;
    private final ProductRepository productRepository;
    private final AccountRepository accountRepository;
    private final PricingService pricingService;
    private final TenantInfoReader tenantInfoReader;

    public OpportunityService(OpportunityRepository opportunityRepository,
                              OpportunityItemRepository opportunityItemRepository,
                              PipelineRepository pipelineRepository,
                              PipelineStageRepository pipelineStageRepository,
                              ProductRepository productRepository,
                              AccountRepository accountRepository,
                              PricingService pricingService,
                              TenantInfoReader tenantInfoReader) {
        this.opportunityRepository = opportunityRepository;
        this.opportunityItemRepository = opportunityItemRepository;
        this.pipelineRepository = pipelineRepository;
        this.pipelineStageRepository = pipelineStageRepository;
        this.productRepository = productRepository;
        this.accountRepository = accountRepository;
        this.pricingService = pricingService;
        this.tenantInfoReader = tenantInfoReader;
    }

    @Transactional
    public Opportunity create(UUID accountId, String name, UUID pipelineId, UUID ownerId, LocalDate expectedClose,
                              UUID leadId) {
        accountRepository.findByIdAndDeletedAtIsNull(accountId)
                .orElseThrow(() -> new NoSuchElementException("Account " + accountId + " nicht gefunden"));
        Pipeline pipeline = pipelineId != null
                ? pipelineRepository.findById(pipelineId)
                        .orElseThrow(() -> new NoSuchElementException("Pipeline " + pipelineId + " nicht gefunden"))
                : pipelineRepository.findByIsDefaultTrue()
                        .orElseThrow(() -> new IllegalStateException("Keine Default-Pipeline vorhanden"));
        PipelineStage firstStage = pipelineStageRepository.findByPipelineIdOrderBySortOrder(pipeline.getId()).stream()
                .filter(stage -> !stage.isWon() && !stage.isLost())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Pipeline hat keine offene Stage"));

        // E-01: eine Waehrung je Mandant — Opportunities laufen immer in der Tenant-Waehrung
        Opportunity opportunity = new Opportunity(TenantContext.get(), accountId, pipeline.getId(),
                firstStage.getId(), name, tenantInfoReader.defaultCurrency());
        opportunity.setOwnerId(ownerId);
        opportunity.setExpectedCloseDate(expectedClose);
        opportunity.setLeadId(leadId);
        return opportunityRepository.save(opportunity);
    }

    @Transactional
    public OpportunityItem addItem(UUID opportunityId, UUID productId, BigDecimal quantity,
                                   BigDecimal unitPriceOverride, BigDecimal discountPct) {
        Opportunity opportunity = load(opportunityId);
        Product product = productRepository.findByIdAndDeletedAtIsNull(productId)
                .filter(Product::isActive)
                .orElseThrow(() -> new NoSuchElementException("Produkt " + productId + " nicht gefunden oder inaktiv"));

        BigDecimal unitPrice = unitPriceOverride;
        if (unitPrice == null) {
            Account account = accountRepository.findByIdAndDeletedAtIsNull(opportunity.getAccountId())
                    .orElseThrow(() -> new NoSuchElementException("Account nicht gefunden"));
            unitPrice = pricingService.resolveUnitPrice(account, product, LocalDate.now());
        }

        int position = opportunityItemRepository.maxPosition(opportunityId) + 10;
        OpportunityItem item = opportunityItemRepository.save(new OpportunityItem(opportunity.getTenantId(),
                opportunityId, productId, quantity, unitPrice, discountPct, position));
        recalculate(opportunity);
        return item;
    }

    @Transactional
    public OpportunityItem updateItem(UUID opportunityId, UUID itemId, BigDecimal quantity,
                                      BigDecimal unitPrice, BigDecimal discountPct) {
        Opportunity opportunity = load(opportunityId);
        OpportunityItem item = opportunityItemRepository.findByIdAndOpportunityId(itemId, opportunityId)
                .orElseThrow(() -> new NoSuchElementException("Position " + itemId + " nicht gefunden"));
        item.update(quantity, unitPrice, discountPct);
        recalculate(opportunity);
        return item;
    }

    @Transactional
    public void removeItem(UUID opportunityId, UUID itemId) {
        Opportunity opportunity = load(opportunityId);
        OpportunityItem item = opportunityItemRepository.findByIdAndOpportunityId(itemId, opportunityId)
                .orElseThrow(() -> new NoSuchElementException("Position " + itemId + " nicht gefunden"));
        opportunityItemRepository.delete(item);
        opportunityItemRepository.flush();
        recalculate(opportunity);
    }

    @Transactional
    public Opportunity estimate(UUID opportunityId, BigDecimal amount) {
        Opportunity opportunity = load(opportunityId);
        if (!opportunityItemRepository.findByOpportunityIdOrderByPosition(opportunityId).isEmpty()) {
            throw new IllegalStateException(
                    "Schaetzbetrag ist nur ohne Positionen erlaubt (E-14); amount wird aus Positionen berechnet");
        }
        opportunity.estimateAmount(amount);
        return opportunity;
    }

    @Transactional
    public Opportunity win(UUID opportunityId) {
        Opportunity opportunity = load(opportunityId);
        opportunity.win(stageFlagged(opportunity.getPipelineId(), true, false).getId());
        return opportunity;
    }

    @Transactional
    public Opportunity lose(UUID opportunityId, String reason) {
        Opportunity opportunity = load(opportunityId);
        opportunity.lose(stageFlagged(opportunity.getPipelineId(), false, true).getId(), reason);
        return opportunity;
    }

    @Transactional
    public Opportunity moveToStage(UUID opportunityId, UUID stageId) {
        Opportunity opportunity = load(opportunityId);
        PipelineStage stage = pipelineStageRepository.findById(stageId)
                .filter(s -> s.getPipelineId().equals(opportunity.getPipelineId()))
                .orElseThrow(() -> new NoSuchElementException(
                        "Stage " + stageId + " gehoert nicht zur Pipeline der Opportunity"));
        if (stage.isWon() || stage.isLost()) {
            throw new IllegalStateException("Won/Lost nur ueber die dedizierten Endpunkte (lost_reason-Pflicht)");
        }
        opportunity.moveToStage(stage.getId());
        return opportunity;
    }

    private void recalculate(Opportunity opportunity) {
        List<OpportunityItem> items = opportunityItemRepository.findByOpportunityIdOrderByPosition(opportunity.getId());
        BigDecimal sum = items.stream().map(OpportunityItem::lineAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        opportunity.recalculateFromItems(sum);
    }

    private Opportunity load(UUID id) {
        return opportunityRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new NoSuchElementException("Opportunity " + id + " nicht gefunden"));
    }

    private PipelineStage stageFlagged(UUID pipelineId, boolean won, boolean lost) {
        return pipelineStageRepository.findByPipelineIdOrderBySortOrder(pipelineId).stream()
                .filter(stage -> stage.isWon() == won && stage.isLost() == lost)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Pipeline hat keine " + (won ? "Won" : "Lost") + "-Stage"));
    }
}
