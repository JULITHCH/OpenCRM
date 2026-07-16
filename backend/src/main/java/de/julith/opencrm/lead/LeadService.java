package de.julith.opencrm.lead;

import de.julith.opencrm.identity.User;
import de.julith.opencrm.identity.UserRepository;
import de.julith.opencrm.shared.audit.AuditService;
import de.julith.opencrm.shared.tenancy.TenantContext;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LeadService {

    private final LeadRepository leadRepository;
    private final LeadAssignmentRepository leadAssignmentRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public LeadService(LeadRepository leadRepository, LeadAssignmentRepository leadAssignmentRepository,
                       UserRepository userRepository, AuditService auditService) {
        this.leadRepository = leadRepository;
        this.leadAssignmentRepository = leadAssignmentRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    /**
     * Manuelle Zuweisung (docs/06, Abschnitt 3.1): setzt owner_id, Statuswechsel NEW -> ASSIGNED,
     * schreibt die Zuweisungshistorie. assignedBy ist die users.id des ausloesenden Nutzers.
     */
    @Transactional
    public Lead assign(UUID leadId, UUID assigneeUserId, String actorKeycloakId) {
        Lead lead = load(leadId);
        User assignee = userRepository.findById(assigneeUserId)
                .filter(User::isActive)
                .orElseThrow(() -> new NoSuchElementException(
                        "Nutzer " + assigneeUserId + " nicht gefunden oder inaktiv"));
        UUID actorId = userRepository.findByKeycloakId(actorKeycloakId).map(User::getId).orElse(null);

        lead.assignTo(assignee.getId());
        leadAssignmentRepository.save(new LeadAssignment(TenantContext.get(), lead.getId(), assignee.getId(),
                actorId, LeadAssignment.Method.MANUAL));
        auditService.record("ASSIGN", "LEAD", lead.getId(), actorId,
                Map.of("assignedTo", assignee.getId().toString(), "method", "MANUAL"));
        return lead;
    }

    @Transactional
    public Lead markContacted(UUID leadId) {
        Lead lead = load(leadId);
        lead.transitionTo(Lead.Status.CONTACTED);
        return lead;
    }

    @Transactional
    public Lead qualify(UUID leadId) {
        Lead lead = load(leadId);
        lead.transitionTo(Lead.Status.QUALIFIED);
        return lead;
    }

    @Transactional
    public Lead disqualify(UUID leadId, String reason) {
        Lead lead = load(leadId);
        lead.disqualify(reason);
        return lead;
    }

    private Lead load(UUID leadId) {
        return leadRepository.findByIdAndDeletedAtIsNull(leadId)
                .orElseThrow(() -> new NoSuchElementException("Lead " + leadId + " nicht gefunden"));
    }
}
