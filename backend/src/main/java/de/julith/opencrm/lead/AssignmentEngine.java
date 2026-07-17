package de.julith.opencrm.lead;

import de.julith.opencrm.shared.tenancy.TenantContext;
import de.julith.opencrm.shared.tenancy.TenantInfoReader;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regelbasierte Zuweisung (docs/06 Abschnitt 3.3): aktive Regeln nach Prioritaet aufsteigend,
 * First-Match; Ziel Nutzer direkt oder Team per Round-Robin; Fallback ist das Default-Team
 * des Tenants (Setting default_team_id, sonst das bei der Provisionierung angelegte Team).
 */
@Component
public class AssignmentEngine {

    private final AssignmentRuleRepository ruleRepository;
    private final RoundRobinAssigner roundRobinAssigner;
    private final LeadAssignmentRepository leadAssignmentRepository;
    private final TenantInfoReader tenantInfoReader;
    private final JdbcTemplate jdbcTemplate;

    public AssignmentEngine(AssignmentRuleRepository ruleRepository, RoundRobinAssigner roundRobinAssigner,
                            LeadAssignmentRepository leadAssignmentRepository, TenantInfoReader tenantInfoReader,
                            JdbcTemplate jdbcTemplate) {
        this.ruleRepository = ruleRepository;
        this.roundRobinAssigner = roundRobinAssigner;
        this.leadAssignmentRepository = leadAssignmentRepository;
        this.tenantInfoReader = tenantInfoReader;
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Wendet die Regeln auf einen unzugewiesenen NEW-Lead an; liefert true bei erfolgter Zuweisung. */
    @Transactional
    public boolean apply(Lead lead) {
        if (lead.getStatus() != Lead.Status.NEW || lead.getOwnerId() != null) {
            return false;
        }
        for (AssignmentRule rule : ruleRepository.findByActiveTrueOrderByPriority()) {
            if (!rule.matches(lead)) {
                continue;
            }
            Optional<UUID> assignee = resolveTarget(rule);
            if (assignee.isPresent()) {
                record(lead, assignee.get(), LeadAssignment.Method.RULE, rule.getId());
                return true;
            }
        }
        return fallbackToDefaultTeam(lead);
    }

    private Optional<UUID> resolveTarget(AssignmentRule rule) {
        if (rule.getTargetType() == AssignmentRule.TargetType.USER) {
            Boolean active = jdbcTemplate.query("SELECT active FROM users WHERE id = ?",
                    rs -> rs.next() ? rs.getBoolean(1) : null, rule.getTargetId());
            return Boolean.TRUE.equals(active) ? Optional.of(rule.getTargetId()) : Optional.empty();
        }
        return roundRobinAssigner.nextAssignee(rule.getTargetId());
    }

    private boolean fallbackToDefaultTeam(Lead lead) {
        UUID teamId = defaultTeamId();
        if (teamId == null) {
            return false;
        }
        Optional<UUID> assignee = roundRobinAssigner.nextAssignee(teamId);
        assignee.ifPresent(userId -> record(lead, userId, LeadAssignment.Method.ROUND_ROBIN, null));
        return assignee.isPresent();
    }

    private UUID defaultTeamId() {
        String configured = tenantInfoReader.settingOrDefault("default_team_id", null);
        if (configured != null) {
            return UUID.fromString(configured);
        }
        List<UUID> teams = jdbcTemplate.query("SELECT id FROM teams ORDER BY created_at LIMIT 1",
                (rs, rowNum) -> rs.getObject(1, UUID.class));
        return teams.isEmpty() ? null : teams.getFirst();
    }

    private void record(Lead lead, UUID assignee, LeadAssignment.Method method, UUID ruleId) {
        lead.assignTo(assignee);
        LeadAssignment assignment = new LeadAssignment(TenantContext.get(), lead.getId(), assignee, null, method);
        if (ruleId != null) {
            assignment.setRuleId(ruleId);
        }
        leadAssignmentRepository.save(assignment);
    }
}
