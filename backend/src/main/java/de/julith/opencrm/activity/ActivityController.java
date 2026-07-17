package de.julith.opencrm.activity;

import de.julith.opencrm.identity.User;
import de.julith.opencrm.identity.UserRepository;
import de.julith.opencrm.shared.tenancy.TenantContext;
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
@RequestMapping("/api/v1/activities")
public class ActivityController {

    private static final String CAN_WRITE = "hasAnyRole('tenant-admin', 'sales-manager', 'sales-rep')";

    private final ActivityRepository activityRepository;
    private final UserRepository userRepository;

    public ActivityController(ActivityRepository activityRepository, UserRepository userRepository) {
        this.activityRepository = activityRepository;
        this.userRepository = userRepository;
    }

    public record ActivityCreateRequest(@NotNull Activity.Type type, @NotBlank String subject, String body,
                                        OffsetDateTime dueAt, UUID leadId, UUID accountId, UUID contactId,
                                        UUID opportunityId) {
    }

    public record ActivityPatchRequest(String subject, String body, OffsetDateTime dueAt) {
    }

    public record ActivityResponse(UUID id, String type, String subject, String body, String dueAt,
                                   String completedAt, UUID ownerId, UUID leadId, UUID accountId, UUID contactId,
                                   UUID opportunityId, String createdAt) {
        static ActivityResponse from(Activity a) {
            return new ActivityResponse(a.getId(), a.getType().name(), a.getSubject(), a.getBody(),
                    a.getDueAt() != null ? a.getDueAt().toString() : null,
                    a.getCompletedAt() != null ? a.getCompletedAt().toString() : null,
                    a.getOwnerId(), a.getLeadId(), a.getAccountId(), a.getContactId(), a.getOpportunityId(),
                    a.getCreatedAt() != null ? a.getCreatedAt().toString() : null);
        }
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<ActivityResponse> list(@RequestParam(required = false) UUID leadId,
                                       @RequestParam(required = false) UUID accountId,
                                       @RequestParam(required = false) UUID contactId,
                                       @RequestParam(required = false) UUID opportunityId,
                                       @RequestParam(required = false) UUID ownerId,
                                       @RequestParam(required = false, defaultValue = "false") boolean openOnly,
                                       @RequestParam(required = false, defaultValue = "50") int limit) {
        return activityRepository.findPage(leadId, accountId, contactId, opportunityId, ownerId, openOnly,
                        PageRequest.ofSize(Math.min(Math.max(limit, 1), 200)))
                .stream().map(ActivityResponse::from).toList();
    }

    @PostMapping
    @PreAuthorize(CAN_WRITE)
    @Transactional
    public ResponseEntity<ActivityResponse> create(@Valid @RequestBody ActivityCreateRequest request,
                                                   @AuthenticationPrincipal Jwt jwt) {
        UUID ownerId = userRepository.findByKeycloakId(jwt.getSubject()).map(User::getId).orElse(null);
        Activity activity = new Activity(TenantContext.get(), request.type(), request.subject(), ownerId);
        activity.setBody(request.body());
        activity.setDueAt(request.dueAt());
        if (request.leadId() != null) {
            activity.linkLead(request.leadId());
        }
        if (request.accountId() != null) {
            activity.linkAccount(request.accountId());
        }
        if (request.contactId() != null) {
            activity.linkContact(request.contactId());
        }
        if (request.opportunityId() != null) {
            activity.linkOpportunity(request.opportunityId());
        }
        Activity saved = activityRepository.save(activity);
        return ResponseEntity.created(URI.create("/api/v1/activities/" + saved.getId()))
                .body(ActivityResponse.from(saved));
    }

    @PatchMapping("/{id}")
    @PreAuthorize(CAN_WRITE)
    @Transactional
    public ActivityResponse patch(@PathVariable UUID id, @RequestBody ActivityPatchRequest request) {
        Activity activity = load(id);
        if (request.subject() != null) {
            activity.setSubject(request.subject());
        }
        if (request.body() != null) {
            activity.setBody(request.body());
        }
        if (request.dueAt() != null) {
            activity.setDueAt(request.dueAt());
        }
        return ActivityResponse.from(activity);
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize(CAN_WRITE)
    @Transactional
    public ActivityResponse complete(@PathVariable UUID id) {
        Activity activity = load(id);
        activity.complete();
        return ActivityResponse.from(activity);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(CAN_WRITE)
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        load(id).softDelete();
        return ResponseEntity.noContent().build();
    }

    private Activity load(UUID id) {
        return activityRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new NoSuchElementException("Aktivitaet " + id + " nicht gefunden"));
    }
}
