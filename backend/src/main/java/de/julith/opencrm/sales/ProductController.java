package de.julith.opencrm.sales;

import de.julith.opencrm.shared.tenancy.TenantContext;
import de.julith.opencrm.shared.tenancy.TenantInfoReader;
import de.julith.opencrm.shared.web.Cursors;
import de.julith.opencrm.shared.web.PageEnvelope;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.net.URI;
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
@RequestMapping("/api/v1/products")
public class ProductController {

    private static final String CAN_MANAGE = "hasAnyRole('tenant-admin', 'sales-manager')";

    private final ProductRepository productRepository;
    private final TenantInfoReader tenantInfoReader;

    public ProductController(ProductRepository productRepository, TenantInfoReader tenantInfoReader) {
        this.productRepository = productRepository;
        this.tenantInfoReader = tenantInfoReader;
    }

    public record ProductCreateRequest(@NotBlank String sku, @NotBlank String name, String description,
                                       String category, String unit, @NotNull BigDecimal listPrice,
                                       BigDecimal taxRate, String externalId) {
    }

    public record ProductPatchRequest(String sku, String name, String description, String category, String unit,
                                      BigDecimal listPrice, BigDecimal taxRate, Boolean active, String externalId) {
    }

    public record ProductResponse(UUID id, String sku, String name, String description, String category, String unit,
                                  BigDecimal listPrice, String currency, BigDecimal taxRate, boolean active,
                                  String externalId, String createdAt) {
        static ProductResponse from(Product p) {
            return new ProductResponse(p.getId(), p.getSku(), p.getName(), p.getDescription(), p.getCategory(),
                    p.getUnit(), p.getListPrice(), p.getCurrency(), p.getTaxRate(), p.isActive(), p.getExternalId(),
                    p.getCreatedAt() != null ? p.getCreatedAt().toString() : null);
        }
    }

    @GetMapping
    @Transactional(readOnly = true)
    public PageEnvelope<ProductResponse> list(@RequestParam(required = false) String q,
                                              @RequestParam(required = false, defaultValue = "false") boolean activeOnly,
                                              @RequestParam(required = false) String cursor,
                                              @RequestParam(required = false) Integer limit) {
        int pageSize = Cursors.clampLimit(limit);
        var pageable = PageRequest.ofSize(pageSize + 1);
        String query = q == null || q.isBlank() ? null : q;
        List<Product> rows = cursor == null
                ? productRepository.findPage(query, activeOnly, pageable)
                : productRepository.findPageAfter(query, activeOnly,
                        Cursors.decode(cursor).createdAt(), Cursors.decode(cursor).id(), pageable);
        return PageEnvelope.of(rows.stream().map(ProductResponse::from).toList(), pageSize,
                r -> Cursors.encode(OffsetDateTime.parse(r.createdAt()), r.id()));
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ProductResponse get(@PathVariable UUID id) {
        return ProductResponse.from(load(id));
    }

    @PostMapping
    @PreAuthorize(CAN_MANAGE)
    @Transactional
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductCreateRequest request) {
        productRepository.findBySkuAndDeletedAtIsNull(request.sku()).ifPresent(existing -> {
            throw new IllegalArgumentException("SKU " + request.sku() + " existiert bereits");
        });
        // E-01: Produktpreise laufen in der Tenant-Waehrung
        Product product = new Product(TenantContext.get(), request.sku(), request.name(), request.listPrice(),
                tenantInfoReader.defaultCurrency());
        product.setDescription(request.description());
        product.setCategory(request.category());
        product.setUnit(request.unit());
        product.setTaxRate(request.taxRate());
        product.setExternalId(request.externalId());
        Product saved = productRepository.save(product);
        return ResponseEntity.created(URI.create("/api/v1/products/" + saved.getId()))
                .body(ProductResponse.from(saved));
    }

    @PatchMapping("/{id}")
    @PreAuthorize(CAN_MANAGE)
    @Transactional
    public ProductResponse patch(@PathVariable UUID id, @RequestBody ProductPatchRequest request) {
        Product product = load(id);
        if (request.sku() != null && !request.sku().equals(product.getSku())) {
            productRepository.findBySkuAndDeletedAtIsNull(request.sku()).ifPresent(existing -> {
                throw new IllegalArgumentException("SKU " + request.sku() + " existiert bereits");
            });
            product.setSku(request.sku());
        }
        if (request.name() != null) {
            product.setName(request.name());
        }
        if (request.description() != null) {
            product.setDescription(request.description());
        }
        if (request.category() != null) {
            product.setCategory(request.category());
        }
        if (request.unit() != null) {
            product.setUnit(request.unit());
        }
        if (request.listPrice() != null) {
            product.setListPrice(request.listPrice());
        }
        if (request.taxRate() != null) {
            product.setTaxRate(request.taxRate());
        }
        if (request.active() != null) {
            product.setActive(request.active());
        }
        if (request.externalId() != null) {
            product.setExternalId(request.externalId());
        }
        return ProductResponse.from(product);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(CAN_MANAGE)
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        load(id).softDelete();
        return ResponseEntity.noContent().build();
    }

    private Product load(UUID id) {
        return productRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new NoSuchElementException("Produkt " + id + " nicht gefunden"));
    }
}
