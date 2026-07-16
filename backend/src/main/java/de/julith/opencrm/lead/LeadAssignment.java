package de.julith.opencrm.lead;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "lead_assignments")
public class LeadAssignment {

    public enum Method { MANUAL, ROUND_ROBIN, RULE }

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "lead_id", nullable = false)
    private UUID leadId;

    @Column(name = "assigned_to", nullable = false)
    private UUID assignedTo;

    @Column(name = "assigned_by")
    private UUID assignedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Method method;

    @Column(name = "rule_id")
    private UUID ruleId;

    @Column(name = "assigned_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime assignedAt;

    protected LeadAssignment() {
    }

    public LeadAssignment(UUID tenantId, UUID leadId, UUID assignedTo, UUID assignedBy, Method method) {
        this.tenantId = tenantId;
        this.leadId = leadId;
        this.assignedTo = assignedTo;
        this.assignedBy = assignedBy;
        this.method = method;
    }

    public UUID getId() {
        return id;
    }

    public UUID getLeadId() {
        return leadId;
    }

    public UUID getAssignedTo() {
        return assignedTo;
    }

    public UUID getAssignedBy() {
        return assignedBy;
    }

    public Method getMethod() {
        return method;
    }

    public UUID getRuleId() {
        return ruleId;
    }

    public OffsetDateTime getAssignedAt() {
        return assignedAt;
    }
}
