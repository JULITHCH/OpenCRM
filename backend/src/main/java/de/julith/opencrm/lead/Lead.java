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
@Table(name = "leads")
public class Lead {

    public enum Source { WEB_FORM, IMPORT, MANUAL, API, EVENT, REFERRAL }

    public enum Status { NEW, ASSIGNED, CONTACTED, QUALIFIED, DISQUALIFIED, CONVERTED }

    @Id
    private UUID id = UUID.randomUUID();

    /**
     * Wird zusätzlich zur RLS-Policy gesetzt: die Policy prüft per WITH CHECK,
     * dass der Wert dem Session-Kontext entspricht — ein falscher Wert schlägt beim INSERT fehl.
     */
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column
    private String title;

    @Column(name = "company_name")
    private String companyName;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @Column
    private String email;

    @Column
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Source source = Source.MANUAL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.NEW;

    @Column(name = "owner_id")
    private UUID ownerId;

    @Column
    private Integer score;

    @Column(name = "disqualified_reason")
    private String disqualifiedReason;

    @Column(name = "converted_at")
    private OffsetDateTime convertedAt;

    @Column(name = "disqualified_at")
    private OffsetDateTime disqualifiedAt;

    @Column(name = "external_id")
    private String externalId;

    /** Custom Fields (E-17): Werte zu custom_field_definitions, u. a. product_interest/region fuers Routing. */
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(nullable = false)
    private java.util.Map<String, Object> custom = new java.util.HashMap<>();

    @Column(name = "converted_account_id")
    private UUID convertedAccountId;

    @Column(name = "converted_contact_id")
    private UUID convertedContactId;

    @Column(name = "converted_opportunity_id")
    private UUID convertedOpportunityId;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected Lead() {
    }

    public Lead(UUID tenantId, String title, String companyName) {
        this.tenantId = tenantId;
        this.title = title;
        this.companyName = companyName;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getTitle() {
        return title;
    }

    public String getCompanyName() {
        return companyName;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public Source getSource() {
        return source;
    }

    public Status getStatus() {
        return status;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public String getExternalId() {
        return externalId;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public java.util.Map<String, Object> getCustom() {
        return custom;
    }

    public void setCustom(java.util.Map<String, Object> custom) {
        this.custom = custom != null ? custom : new java.util.HashMap<>();
    }

    public void markConverted(UUID accountId, UUID contactId, UUID opportunityId) {
        transitionTo(Status.CONVERTED);
        this.convertedAccountId = accountId;
        this.convertedContactId = contactId;
        this.convertedOpportunityId = opportunityId;
    }

    public UUID getConvertedAccountId() {
        return convertedAccountId;
    }

    public UUID getConvertedContactId() {
        return convertedContactId;
    }

    public UUID getConvertedOpportunityId() {
        return convertedOpportunityId;
    }

    public void setSource(Source source) {
        this.source = source;
    }

    public Integer getScore() {
        return score;
    }

    public String getDisqualifiedReason() {
        return disqualifiedReason;
    }

    public OffsetDateTime getConvertedAt() {
        return convertedAt;
    }

    public OffsetDateTime getDisqualifiedAt() {
        return disqualifiedAt;
    }

    public OffsetDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setScore(Integer score) {
        this.score = score;
    }

    /**
     * Zuweisung an einen Verkäufer (docs/06 Abschnitt 3): setzt owner_id, Statuswechsel NEW -> ASSIGNED.
     * Terminale Leads (DISQUALIFIED/CONVERTED) sind nicht zuweisbar.
     */
    public void assignTo(UUID userId) {
        if (this.status == Status.DISQUALIFIED || this.status == Status.CONVERTED) {
            throw new IllegalStateException(
                    "Lead im Status " + this.status + " kann nicht zugewiesen werden");
        }
        this.ownerId = userId;
        if (this.status == Status.NEW) {
            this.status = Status.ASSIGNED;
        }
    }

    /** Erlaubte Statusübergänge laut Lifecycle in docs/06-lead-management.md (inkl. Reaktivierung). */
    private static final java.util.Map<Status, java.util.Set<Status>> TRANSITIONS = java.util.Map.of(
            Status.NEW, java.util.Set.of(Status.ASSIGNED, Status.DISQUALIFIED),
            Status.ASSIGNED, java.util.Set.of(Status.CONTACTED, Status.DISQUALIFIED),
            Status.CONTACTED, java.util.Set.of(Status.QUALIFIED, Status.DISQUALIFIED),
            Status.QUALIFIED, java.util.Set.of(Status.CONVERTED, Status.DISQUALIFIED),
            Status.DISQUALIFIED, java.util.Set.of(Status.NEW),
            Status.CONVERTED, java.util.Set.of());

    public void transitionTo(Status target) {
        if (!TRANSITIONS.get(this.status).contains(target)) {
            throw new IllegalStateException(
                    "Statusuebergang " + this.status + " -> " + target + " ist nicht erlaubt");
        }
        this.status = target;
        if (target == Status.CONVERTED) {
            this.convertedAt = OffsetDateTime.now();
        }
        if (target == Status.DISQUALIFIED) {
            this.disqualifiedAt = OffsetDateTime.now();
        }
    }

    public void disqualify(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("disqualified_reason ist bei Disqualifikation Pflicht");
        }
        transitionTo(Status.DISQUALIFIED);
        this.disqualifiedReason = reason;
    }

    /** Reaktivierung eines disqualifizierten Leads (docs/06 Abschnitt 2): zurück auf NEW, Felder leeren. */
    public void reactivate() {
        transitionTo(Status.NEW);
        this.disqualifiedReason = null;
        this.disqualifiedAt = null;
    }

    public void softDelete() {
        this.deletedAt = OffsetDateTime.now();
    }
}
