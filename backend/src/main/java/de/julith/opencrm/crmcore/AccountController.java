package de.julith.opencrm.crmcore;

import de.julith.opencrm.shared.tenancy.TenantContext;
import de.julith.opencrm.shared.web.Cursors;
import de.julith.opencrm.shared.web.PageEnvelope;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.net.URI;
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
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private static final String CAN_WRITE = "hasAnyRole('tenant-admin', 'sales-manager', 'sales-rep')";

    private final AccountRepository accountRepository;

    public AccountController(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public record AccountUpsertRequest(@NotBlank String name, String industry, String website, String street,
                                       String postalCode, String city, String country, UUID ownerId,
                                       String externalId) {
    }

    public record AccountPatchRequest(String name, String industry, String website, String street,
                                      String postalCode, String city, String country, UUID ownerId,
                                      String externalId) {
    }

    public record AccountResponse(UUID id, String name, String industry, String website, String street,
                                  String postalCode, String city, String country, UUID ownerId, String externalId,
                                  String createdAt) {
        static AccountResponse from(Account a) {
            return new AccountResponse(a.getId(), a.getName(), a.getIndustry(), a.getWebsite(), a.getStreet(),
                    a.getPostalCode(), a.getCity(), a.getCountry(), a.getOwnerId(), a.getExternalId(),
                    a.getCreatedAt() != null ? a.getCreatedAt().toString() : null);
        }
    }

    @GetMapping
    @Transactional(readOnly = true)
    public PageEnvelope<AccountResponse> list(@RequestParam(required = false) String q,
                                              @RequestParam(required = false) String cursor,
                                              @RequestParam(required = false) Integer limit) {
        int pageSize = Cursors.clampLimit(limit);
        var pageable = PageRequest.ofSize(pageSize + 1);
        List<Account> rows = cursor == null
                ? accountRepository.findPage(emptyToNull(q), pageable)
                : findAfter(q, cursor, pageable);
        return PageEnvelope.of(rows.stream().map(AccountResponse::from).toList(), pageSize,
                r -> Cursors.encode(java.time.OffsetDateTime.parse(r.createdAt()), r.id()));
    }

    private List<Account> findAfter(String q, String cursor, PageRequest pageable) {
        Cursors.Cursor decoded = Cursors.decode(cursor);
        return accountRepository.findPageAfter(emptyToNull(q), decoded.createdAt(), decoded.id(), pageable);
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public AccountResponse get(@PathVariable UUID id) {
        return AccountResponse.from(load(id));
    }

    @PostMapping
    @PreAuthorize(CAN_WRITE)
    @Transactional
    public ResponseEntity<AccountResponse> create(@Valid @RequestBody AccountUpsertRequest request) {
        Account account = new Account(TenantContext.get(), request.name());
        applyOptionalFields(account, new AccountPatchRequest(null, request.industry(), request.website(),
                request.street(), request.postalCode(), request.city(), request.country(), request.ownerId(),
                request.externalId()));
        Account saved = accountRepository.save(account);
        return ResponseEntity.created(URI.create("/api/v1/accounts/" + saved.getId()))
                .body(AccountResponse.from(saved));
    }

    @PatchMapping("/{id}")
    @PreAuthorize(CAN_WRITE)
    @Transactional
    public AccountResponse patch(@PathVariable UUID id, @RequestBody AccountPatchRequest request) {
        Account account = load(id);
        if (request.name() != null) {
            if (request.name().isBlank()) {
                throw new IllegalArgumentException("name darf nicht leer sein");
            }
            account.setName(request.name());
        }
        applyOptionalFields(account, request);
        return AccountResponse.from(account);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('tenant-admin', 'sales-manager')")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        load(id).softDelete();
        return ResponseEntity.noContent().build();
    }

    private Account load(UUID id) {
        return accountRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new NoSuchElementException("Account " + id + " nicht gefunden"));
    }

    private static void applyOptionalFields(Account account, AccountPatchRequest request) {
        if (request.industry() != null) {
            account.setIndustry(request.industry());
        }
        if (request.website() != null) {
            account.setWebsite(request.website());
        }
        if (request.street() != null) {
            account.setStreet(request.street());
        }
        if (request.postalCode() != null) {
            account.setPostalCode(request.postalCode());
        }
        if (request.city() != null) {
            account.setCity(request.city());
        }
        if (request.country() != null) {
            account.setCountry(request.country());
        }
        if (request.ownerId() != null) {
            account.setOwnerId(request.ownerId());
        }
        if (request.externalId() != null) {
            account.setExternalId(request.externalId());
        }
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
