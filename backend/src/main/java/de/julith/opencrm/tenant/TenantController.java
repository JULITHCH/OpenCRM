package de.julith.opencrm.tenant;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenants")
@PreAuthorize("hasRole('platform-admin')")
public class TenantController {

    private final TenantRepository tenantRepository;
    private final TenantProvisioningService provisioningService;

    public TenantController(TenantRepository tenantRepository, TenantProvisioningService provisioningService) {
        this.tenantRepository = tenantRepository;
        this.provisioningService = provisioningService;
    }

    public record TenantCreateRequest(
            @NotBlank String name,
            @NotBlank @Pattern(regexp = "[a-z0-9]([a-z0-9-]{0,38}[a-z0-9])?",
                    message = "slug: nur Kleinbuchstaben, Ziffern und Bindestriche") String slug) {
    }

    public record TenantResponse(UUID id, String name, String slug, String status, String plan,
                                 String defaultCurrency) {
        static TenantResponse from(Tenant tenant) {
            return new TenantResponse(tenant.getId(), tenant.getName(), tenant.getSlug(),
                    tenant.getStatus().name(), tenant.getPlan(), tenant.getDefaultCurrency());
        }
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<TenantResponse> list() {
        return tenantRepository.findAll().stream().map(TenantResponse::from).toList();
    }

    @PostMapping
    public ResponseEntity<TenantResponse> create(@Valid @RequestBody TenantCreateRequest request) {
        Tenant tenant = provisioningService.provision(request.name(), request.slug());
        return ResponseEntity.created(URI.create("/api/v1/tenants/" + tenant.getId()))
                .body(TenantResponse.from(tenant));
    }
}
