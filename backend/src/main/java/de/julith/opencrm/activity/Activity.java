package de.julith.opencrm.activity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "activities")
public class Activity {

    public enum Type { CALL, EMAIL, MEETING, NOTE, TASK }

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Type type;

    @Column(nullable = false)
    private String subject;

    @Column
    private String body;

    @Column(name = "due_at")
    private OffsetDateTime dueAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "owner_id")
    private UUID ownerId;

    @Column(name = "lead_id")
    private UUID leadId;

    @Column(name = "account_id")
    private UUID accountId;

    @Column(name = "contact_id")
    private UUID contactId;

    @Column(name = "opportunity_id")
    private UUID opportunityId;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected Activity() {
    }

    public Activity(UUID tenantId, Type type, String subject, UUID ownerId) {
        this.tenantId = tenantId;
        this.type = type;
        this.subject = subject;
        this.ownerId = ownerId;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public Type getType() {
        return type;
    }

    public String getSubject() {
        return subject;
    }

    public String getBody() {
        return body;
    }

    public OffsetDateTime getDueAt() {
        return dueAt;
    }

    public OffsetDateTime getCompletedAt() {
        return completedAt;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public UUID getLeadId() {
        return leadId;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public UUID getContactId() {
        return contactId;
    }

    public UUID getOpportunityId() {
        return opportunityId;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public void setDueAt(OffsetDateTime dueAt) {
        this.dueAt = dueAt;
    }

    public void linkLead(UUID leadId) {
        this.leadId = leadId;
    }

    public void linkAccount(UUID accountId) {
        this.accountId = accountId;
    }

    public void linkContact(UUID contactId) {
        this.contactId = contactId;
    }

    public void linkOpportunity(UUID opportunityId) {
        this.opportunityId = opportunityId;
    }

    public void complete() {
        if (this.completedAt != null) {
            throw new IllegalStateException("Aktivitaet ist bereits abgeschlossen");
        }
        this.completedAt = OffsetDateTime.now();
    }

    public void softDelete() {
        this.deletedAt = OffsetDateTime.now();
    }
}
