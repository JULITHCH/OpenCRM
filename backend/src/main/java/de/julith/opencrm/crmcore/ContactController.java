package de.julith.opencrm.crmcore;

import de.julith.opencrm.shared.tenancy.TenantContext;
import de.julith.opencrm.shared.web.Cursors;
import de.julith.opencrm.shared.web.PageEnvelope;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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
@RequestMapping("/api/v1/contacts")
public class ContactController {

    private static final String CAN_WRITE = "hasAnyRole('tenant-admin', 'sales-manager', 'sales-rep')";

    private final ContactRepository contactRepository;
    private final AccountRepository accountRepository;

    public ContactController(ContactRepository contactRepository, AccountRepository accountRepository) {
        this.contactRepository = contactRepository;
        this.accountRepository = accountRepository;
    }

    public record ContactCreateRequest(@NotBlank String lastName, String firstName, UUID accountId, String email,
                                       String phone, String position, String externalId) {
    }

    public record ContactPatchRequest(String lastName, String firstName, UUID accountId, String email,
                                      String phone, String position, String externalId) {
    }

    public record ContactResponse(UUID id, String lastName, String firstName, UUID accountId, String email,
                                  String phone, String position, String externalId, String createdAt) {
        static ContactResponse from(Contact c) {
            return new ContactResponse(c.getId(), c.getLastName(), c.getFirstName(), c.getAccountId(), c.getEmail(),
                    c.getPhone(), c.getPosition(), c.getExternalId(),
                    c.getCreatedAt() != null ? c.getCreatedAt().toString() : null);
        }
    }

    @GetMapping
    @Transactional(readOnly = true)
    public PageEnvelope<ContactResponse> list(@RequestParam(required = false) String q,
                                              @RequestParam(required = false) UUID accountId,
                                              @RequestParam(required = false) String cursor,
                                              @RequestParam(required = false) Integer limit) {
        int pageSize = Cursors.clampLimit(limit);
        var pageable = PageRequest.ofSize(pageSize + 1);
        String query = q == null || q.isBlank() ? null : q;
        List<Contact> rows;
        if (cursor == null) {
            rows = contactRepository.findPage(query, accountId, pageable);
        } else {
            Cursors.Cursor decoded = Cursors.decode(cursor);
            rows = contactRepository.findPageAfter(query, accountId, decoded.createdAt(), decoded.id(), pageable);
        }
        return PageEnvelope.of(rows.stream().map(ContactResponse::from).toList(), pageSize,
                r -> Cursors.encode(OffsetDateTime.parse(r.createdAt()), r.id()));
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ContactResponse get(@PathVariable UUID id) {
        return ContactResponse.from(load(id));
    }

    @PostMapping
    @PreAuthorize(CAN_WRITE)
    @Transactional
    public ResponseEntity<ContactResponse> create(@Valid @RequestBody ContactCreateRequest request) {
        Contact contact = new Contact(TenantContext.get(), request.lastName());
        if (request.accountId() != null) {
            requireAccount(request.accountId());
            contact.setAccountId(request.accountId());
        }
        contact.setFirstName(request.firstName());
        contact.setEmail(request.email());
        contact.setPhone(request.phone());
        contact.setPosition(request.position());
        contact.setExternalId(request.externalId());
        Contact saved = contactRepository.save(contact);
        return ResponseEntity.created(URI.create("/api/v1/contacts/" + saved.getId()))
                .body(ContactResponse.from(saved));
    }

    @PatchMapping("/{id}")
    @PreAuthorize(CAN_WRITE)
    @Transactional
    public ContactResponse patch(@PathVariable UUID id, @RequestBody ContactPatchRequest request) {
        Contact contact = load(id);
        if (request.lastName() != null) {
            if (request.lastName().isBlank()) {
                throw new IllegalArgumentException("lastName darf nicht leer sein");
            }
            contact.setLastName(request.lastName());
        }
        if (request.accountId() != null) {
            requireAccount(request.accountId());
            contact.setAccountId(request.accountId());
        }
        if (request.firstName() != null) {
            contact.setFirstName(request.firstName());
        }
        if (request.email() != null) {
            contact.setEmail(request.email());
        }
        if (request.phone() != null) {
            contact.setPhone(request.phone());
        }
        if (request.position() != null) {
            contact.setPosition(request.position());
        }
        if (request.externalId() != null) {
            contact.setExternalId(request.externalId());
        }
        return ContactResponse.from(contact);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('tenant-admin', 'sales-manager')")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        load(id).softDelete();
        return ResponseEntity.noContent().build();
    }

    private Contact load(UUID id) {
        return contactRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new NoSuchElementException("Contact " + id + " nicht gefunden"));
    }

    private void requireAccount(UUID accountId) {
        accountRepository.findByIdAndDeletedAtIsNull(accountId)
                .orElseThrow(() -> new NoSuchElementException("Account " + accountId + " nicht gefunden"));
    }
}
