package de.julith.opencrm.lead;

import de.julith.opencrm.shared.tenancy.TenantContext;
import de.julith.opencrm.shared.web.Cursors;
import de.julith.opencrm.shared.web.PageEnvelope;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/leads")
public class LeadController {

    private static final String CAN_WRITE = "hasAnyRole('tenant-admin', 'sales-manager', 'sales-rep')";
    private static final String CAN_ASSIGN = "hasAnyRole('tenant-admin', 'sales-manager')";

    private final LeadRepository leadRepository;
    private final LeadAssignmentRepository leadAssignmentRepository;
    private final LeadService leadService;

    public LeadController(LeadRepository leadRepository, LeadAssignmentRepository leadAssignmentRepository,
                          LeadService leadService) {
        this.leadRepository = leadRepository;
        this.leadAssignmentRepository = leadAssignmentRepository;
        this.leadService = leadService;
    }

    public record LeadCreateRequest(@NotBlank String title, String companyName, String firstName, String lastName,
                                    String email, String phone, Lead.Source source, String externalId) {
    }

    public record LeadPatchRequest(String title, String companyName, String firstName, String lastName,
                                   String email, String phone, Integer score) {
    }

    public record AssignRequest(@NotNull UUID userId) {
    }

    public record DisqualifyRequest(@NotBlank String reason) {
    }

    public record LeadResponse(UUID id, String title, String companyName, String firstName, String lastName,
                               String email, String phone, String source, String status, Integer score,
                               UUID ownerId, String disqualifiedReason, String externalId, String createdAt) {
        static LeadResponse from(Lead lead) {
            return new LeadResponse(lead.getId(), lead.getTitle(), lead.getCompanyName(), lead.getFirstName(),
                    lead.getLastName(), lead.getEmail(), lead.getPhone(), lead.getSource().name(),
                    lead.getStatus().name(), lead.getScore(), lead.getOwnerId(), lead.getDisqualifiedReason(),
                    lead.getExternalId(), lead.getCreatedAt() != null ? lead.getCreatedAt().toString() : null);
        }
    }

    public record AssignmentResponse(UUID id, UUID assignedTo, UUID assignedBy, String method, String assignedAt) {
        static AssignmentResponse from(LeadAssignment assignment) {
            return new AssignmentResponse(assignment.getId(), assignment.getAssignedTo(),
                    assignment.getAssignedBy(), assignment.getMethod().name(),
                    assignment.getAssignedAt() != null ? assignment.getAssignedAt().toString() : null);
        }
    }

    @GetMapping
    @Transactional(readOnly = true)
    public PageEnvelope<LeadResponse> list(@RequestParam(required = false) String q,
                                           @RequestParam(required = false) Lead.Status status,
                                           @RequestParam(required = false) UUID ownerId,
                                           @RequestParam(required = false) String cursor,
                                           @RequestParam(required = false) Integer limit) {
        int pageSize = Cursors.clampLimit(limit);
        var pageable = PageRequest.ofSize(pageSize + 1);
        String query = q == null || q.isBlank() ? null : q;
        List<Lead> rows;
        if (cursor == null) {
            rows = leadRepository.findPage(query, status, ownerId, pageable);
        } else {
            Cursors.Cursor decoded = Cursors.decode(cursor);
            rows = leadRepository.findPageAfter(query, status, ownerId, decoded.createdAt(), decoded.id(), pageable);
        }
        return PageEnvelope.of(rows.stream().map(LeadResponse::from).toList(), pageSize,
                r -> Cursors.encode(OffsetDateTime.parse(r.createdAt()), r.id()));
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public LeadResponse get(@PathVariable UUID id) {
        return LeadResponse.from(load(id));
    }

    @PostMapping
    @PreAuthorize(CAN_WRITE)
    @Transactional
    public ResponseEntity<LeadResponse> create(@Valid @RequestBody LeadCreateRequest request) {
        if (isBlank(request.companyName()) && isBlank(request.lastName())) {
            throw new IllegalArgumentException("Mindestens eines von companyName und lastName ist Pflicht");
        }
        Lead lead = new Lead(TenantContext.get(), request.title(), request.companyName());
        lead.setFirstName(request.firstName());
        lead.setLastName(request.lastName());
        lead.setEmail(request.email());
        lead.setPhone(request.phone());
        if (request.source() != null) {
            lead.setSource(request.source());
        }
        Lead saved = leadRepository.save(lead);
        return ResponseEntity.created(URI.create("/api/v1/leads/" + saved.getId())).body(LeadResponse.from(saved));
    }

    @PatchMapping("/{id}")
    @PreAuthorize(CAN_WRITE)
    @Transactional
    public LeadResponse patch(@PathVariable UUID id, @RequestBody LeadPatchRequest request) {
        Lead lead = load(id);
        if (request.title() != null) {
            lead.setTitle(request.title());
        }
        if (request.companyName() != null) {
            lead.setCompanyName(request.companyName());
        }
        if (request.firstName() != null) {
            lead.setFirstName(request.firstName());
        }
        if (request.lastName() != null) {
            lead.setLastName(request.lastName());
        }
        if (request.email() != null) {
            lead.setEmail(request.email());
        }
        if (request.phone() != null) {
            lead.setPhone(request.phone());
        }
        if (request.score() != null) {
            lead.setScore(request.score());
        }
        if (isBlank(lead.getCompanyName()) && isBlank(lead.getLastName())) {
            throw new IllegalArgumentException("Mindestens eines von companyName und lastName ist Pflicht");
        }
        return LeadResponse.from(lead);
    }

    @PostMapping("/{id}/assign")
    @PreAuthorize(CAN_ASSIGN)
    public LeadResponse assign(@PathVariable UUID id, @Valid @RequestBody AssignRequest request,
                               @AuthenticationPrincipal Jwt jwt) {
        return LeadResponse.from(leadService.assign(id, request.userId(), jwt.getSubject()));
    }

    @PostMapping("/{id}/contacted")
    @PreAuthorize(CAN_WRITE)
    public LeadResponse contacted(@PathVariable UUID id) {
        return LeadResponse.from(leadService.markContacted(id));
    }

    @PostMapping("/{id}/qualify")
    @PreAuthorize(CAN_WRITE)
    public LeadResponse qualify(@PathVariable UUID id) {
        return LeadResponse.from(leadService.qualify(id));
    }

    @PostMapping("/{id}/disqualify")
    @PreAuthorize(CAN_WRITE)
    public LeadResponse disqualify(@PathVariable UUID id, @Valid @RequestBody DisqualifyRequest request) {
        return LeadResponse.from(leadService.disqualify(id, request.reason()));
    }

    @GetMapping("/{id}/assignments")
    @Transactional(readOnly = true)
    public List<AssignmentResponse> assignments(@PathVariable UUID id) {
        load(id);
        return leadAssignmentRepository.findByLeadIdOrderByAssignedAtDesc(id).stream()
                .map(AssignmentResponse::from).toList();
    }

    private Lead load(UUID id) {
        return leadRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new NoSuchElementException("Lead " + id + " nicht gefunden"));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
