package de.julith.opencrm.lead;

import de.julith.opencrm.shared.tenancy.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/assignment-rules")
@PreAuthorize("hasAnyRole('tenant-admin', 'sales-manager')")
public class AssignmentRuleController {

    private final AssignmentRuleRepository ruleRepository;

    public AssignmentRuleController(AssignmentRuleRepository ruleRepository) {
        this.ruleRepository = ruleRepository;
    }

    public record RuleCreateRequest(@NotBlank String name, @NotNull Integer priority, Map<String, Object> criteria,
                                    @NotNull AssignmentRule.TargetType targetType, @NotNull UUID targetId,
                                    AssignmentRule.Strategy strategy) {
    }

    public record RulePatchRequest(Boolean active) {
    }

    public record RuleResponse(UUID id, String name, int priority, boolean active, Map<String, Object> criteria,
                               String targetType, UUID targetId, String strategy) {
        static RuleResponse from(AssignmentRule rule) {
            return new RuleResponse(rule.getId(), rule.getName(), rule.getPriority(), rule.isActive(),
                    rule.getCriteria(), rule.getTargetType().name(), rule.getTargetId(), rule.getStrategy().name());
        }
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<RuleResponse> list() {
        return ruleRepository.findAllByOrderByPriority().stream().map(RuleResponse::from).toList();
    }

    @PostMapping
    @Transactional
    public ResponseEntity<RuleResponse> create(@Valid @RequestBody RuleCreateRequest request) {
        AssignmentRule.Strategy strategy = request.strategy() != null
                ? request.strategy()
                : request.targetType() == AssignmentRule.TargetType.TEAM
                        ? AssignmentRule.Strategy.ROUND_ROBIN
                        : AssignmentRule.Strategy.DIRECT;
        AssignmentRule rule = ruleRepository.save(new AssignmentRule(TenantContext.get(), request.name(),
                request.priority(), request.criteria(), request.targetType(), request.targetId(), strategy));
        return ResponseEntity.created(URI.create("/api/v1/assignment-rules/" + rule.getId()))
                .body(RuleResponse.from(rule));
    }

    @PatchMapping("/{id}")
    @Transactional
    public RuleResponse patch(@PathVariable UUID id, @RequestBody RulePatchRequest request) {
        AssignmentRule rule = ruleRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Regel " + id + " nicht gefunden"));
        if (request.active() != null) {
            rule.setActive(request.active());
        }
        return RuleResponse.from(rule);
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        ruleRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Regel " + id + " nicht gefunden"));
        ruleRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
