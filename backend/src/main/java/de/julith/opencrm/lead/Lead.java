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

    @Column(name = "external_id")
    private String externalId;

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

    public void setSource(Source source) {
        this.source = source;
    }

    public void assignTo(UUID userId) {
        this.ownerId = userId;
        if (this.status == Status.NEW) {
            this.status = Status.ASSIGNED;
        }
    }
}
