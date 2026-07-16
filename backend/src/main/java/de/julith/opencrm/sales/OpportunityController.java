package de.julith.opencrm.sales;

import de.julith.opencrm.shared.web.Cursors;
import de.julith.opencrm.shared.web.PageEnvelope;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/opportunities")
public class OpportunityController {

    private static final String CAN_WRITE = "hasAnyRole('tenant-admin', 'sales-manager', 'sales-rep')";

    private final OpportunityRepository opportunityRepository;
    private final OpportunityItemRepository opportunityItemRepository;
    private final OpportunityService opportunityService;

    public OpportunityController(OpportunityRepository opportunityRepository,
                                 OpportunityItemRepository opportunityItemRepository,
                                 OpportunityService opportunityService) {
        this.opportunityRepository = opportunityRepository;
        this.opportunityItemRepository = opportunityItemRepository;
        this.opportunityService = opportunityService;
    }

    public record OpportunityCreateRequest(@NotNull UUID accountId, @NotBlank String name, UUID pipelineId,
                                           UUID ownerId, LocalDate expectedCloseDate) {
    }

    public record OpportunityPatchRequest(String name, LocalDate expectedCloseDate, UUID ownerId, UUID stageId) {
    }

    public record EstimateRequest(@NotNull BigDecimal amount) {
    }

    public record LoseRequest(@NotBlank String reason) {
    }

    public record ItemRequest(@NotNull UUID productId, @NotNull BigDecimal quantity, BigDecimal unitPrice,
                              BigDecimal discountPct) {
    }

    public record ItemPatchRequest(BigDecimal quantity, BigDecimal unitPrice, BigDecimal discountPct) {
    }

    public record ItemResponse(UUID id, UUID productId, BigDecimal quantity, BigDecimal unitPrice,
                               BigDecimal discountPct, int position, BigDecimal lineAmount) {
        static ItemResponse from(OpportunityItem item) {
            return new ItemResponse(item.getId(), item.getProductId(), item.getQuantity(), item.getUnitPrice(),
                    item.getDiscountPct(), item.getPosition(), item.lineAmount());
        }
    }

    public record OpportunityResponse(UUID id, UUID accountId, UUID pipelineId, UUID stageId, String name,
                                      BigDecimal amount, boolean isEstimated, String currency,
                                      LocalDate expectedCloseDate, UUID ownerId, UUID leadId, String status,
                                      String wonAt, String lostAt, String lostReason, String createdAt) {
        static OpportunityResponse from(Opportunity o) {
            return new OpportunityResponse(o.getId(), o.getAccountId(), o.getPipelineId(), o.getStageId(),
                    o.getName(), o.getAmount(), o.isEstimated(), o.getCurrency(), o.getExpectedCloseDate(),
                    o.getOwnerId(), o.getLeadId(), o.getStatus().name(),
                    o.getWonAt() != null ? o.getWonAt().toString() : null,
                    o.getLostAt() != null ? o.getLostAt().toString() : null,
                    o.getLostReason(), o.getCreatedAt() != null ? o.getCreatedAt().toString() : null);
        }
    }

    @GetMapping
    @Transactional(readOnly = true)
    public PageEnvelope<OpportunityResponse> list(@RequestParam(required = false) String q,
                                                  @RequestParam(required = false) Opportunity.Status status,
                                                  @RequestParam(required = false) UUID accountId,
                                                  @RequestParam(required = false) UUID ownerId,
                                                  @RequestParam(required = false) String cursor,
                                                  @RequestParam(required = false) Integer limit) {
        int pageSize = Cursors.clampLimit(limit);
        var pageable = PageRequest.ofSize(pageSize + 1);
        String query = q == null || q.isBlank() ? null : q;
        List<Opportunity> rows;
        if (cursor == null) {
            rows = opportunityRepository.findPage(query, status, accountId, ownerId, pageable);
        } else {
            Cursors.Cursor decoded = Cursors.decode(cursor);
            rows = opportunityRepository.findPageAfter(query, status, accountId, ownerId,
                    decoded.createdAt(), decoded.id(), pageable);
        }
        return PageEnvelope.of(rows.stream().map(OpportunityResponse::from).toList(), pageSize,
                r -> Cursors.encode(OffsetDateTime.parse(r.createdAt()), r.id()));
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public OpportunityResponse get(@PathVariable UUID id) {
        return OpportunityResponse.from(load(id));
    }

    @PostMapping
    @PreAuthorize(CAN_WRITE)
    public ResponseEntity<OpportunityResponse> create(@Valid @RequestBody OpportunityCreateRequest request) {
        Opportunity opportunity = opportunityService.create(request.accountId(), request.name(),
                request.pipelineId(), request.ownerId(), request.expectedCloseDate(), null);
        return ResponseEntity.created(URI.create("/api/v1/opportunities/" + opportunity.getId()))
                .body(OpportunityResponse.from(opportunity));
    }

    @PatchMapping("/{id}")
    @PreAuthorize(CAN_WRITE)
    @Transactional
    public OpportunityResponse patch(@PathVariable UUID id, @RequestBody OpportunityPatchRequest request) {
        Opportunity opportunity = load(id);
        if (request.name() != null) {
            opportunity.setName(request.name());
        }
        if (request.expectedCloseDate() != null) {
            opportunity.setExpectedCloseDate(request.expectedCloseDate());
        }
        if (request.ownerId() != null) {
            opportunity.setOwnerId(request.ownerId());
        }
        if (request.stageId() != null) {
            opportunityService.moveToStage(id, request.stageId());
        }
        return OpportunityResponse.from(opportunity);
    }

    @PostMapping("/{id}/estimate")
    @PreAuthorize(CAN_WRITE)
    public OpportunityResponse estimate(@PathVariable UUID id, @Valid @RequestBody EstimateRequest request) {
        return OpportunityResponse.from(opportunityService.estimate(id, request.amount()));
    }

    @PostMapping("/{id}/won")
    @PreAuthorize(CAN_WRITE)
    public OpportunityResponse won(@PathVariable UUID id) {
        return OpportunityResponse.from(opportunityService.win(id));
    }

    @PostMapping("/{id}/lost")
    @PreAuthorize(CAN_WRITE)
    public OpportunityResponse lost(@PathVariable UUID id, @Valid @RequestBody LoseRequest request) {
        return OpportunityResponse.from(opportunityService.lose(id, request.reason()));
    }

    @GetMapping("/{id}/items")
    @Transactional(readOnly = true)
    public List<ItemResponse> items(@PathVariable UUID id) {
        load(id);
        return opportunityItemRepository.findByOpportunityIdOrderByPosition(id).stream()
                .map(ItemResponse::from).toList();
    }

    @PostMapping("/{id}/items")
    @PreAuthorize(CAN_WRITE)
    public ResponseEntity<ItemResponse> addItem(@PathVariable UUID id, @Valid @RequestBody ItemRequest request) {
        OpportunityItem item = opportunityService.addItem(id, request.productId(), request.quantity(),
                request.unitPrice(), request.discountPct());
        return ResponseEntity.created(URI.create("/api/v1/opportunities/" + id + "/items/" + item.getId()))
                .body(ItemResponse.from(item));
    }

    @PatchMapping("/{id}/items/{itemId}")
    @PreAuthorize(CAN_WRITE)
    public ItemResponse updateItem(@PathVariable UUID id, @PathVariable UUID itemId,
                                   @RequestBody ItemPatchRequest request) {
        return ItemResponse.from(opportunityService.updateItem(id, itemId, request.quantity(),
                request.unitPrice(), request.discountPct()));
    }

    @DeleteMapping("/{id}/items/{itemId}")
    @PreAuthorize(CAN_WRITE)
    public ResponseEntity<Void> removeItem(@PathVariable UUID id, @PathVariable UUID itemId) {
        opportunityService.removeItem(id, itemId);
        return ResponseEntity.noContent().build();
    }

    private Opportunity load(UUID id) {
        return opportunityRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new NoSuchElementException("Opportunity " + id + " nicht gefunden"));
    }
}
