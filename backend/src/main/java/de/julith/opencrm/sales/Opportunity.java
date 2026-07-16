package de.julith.opencrm.sales;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "opportunities")
public class Opportunity {

    public enum Status { OPEN, WON, LOST }

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "pipeline_id", nullable = false)
    private UUID pipelineId;

    @Column(name = "stage_id", nullable = false)
    private UUID stageId;

    @Column(nullable = false)
    private String name;

    /** Denormalisierte Positionssumme; bei is_estimated manueller Schätzwert (E-14). */
    @Column(nullable = false)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(name = "is_estimated", nullable = false)
    private boolean isEstimated;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "expected_close_date")
    private LocalDate expectedCloseDate;

    @Column(name = "owner_id")
    private UUID ownerId;

    @Column(name = "lead_id")
    private UUID leadId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.OPEN;

    @Column(name = "won_at")
    private OffsetDateTime wonAt;

    @Column(name = "lost_at")
    private OffsetDateTime lostAt;

    @Column(name = "lost_reason")
    private String lostReason;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected Opportunity() {
    }

    public Opportunity(UUID tenantId, UUID accountId, UUID pipelineId, UUID stageId, String name, String currency) {
        this.tenantId = tenantId;
        this.accountId = accountId;
        this.pipelineId = pipelineId;
        this.stageId = stageId;
        this.name = name;
        this.currency = currency;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public UUID getPipelineId() {
        return pipelineId;
    }

    public UUID getStageId() {
        return stageId;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public boolean isEstimated() {
        return isEstimated;
    }

    public String getCurrency() {
        return currency;
    }

    public LocalDate getExpectedCloseDate() {
        return expectedCloseDate;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public UUID getLeadId() {
        return leadId;
    }

    public Status getStatus() {
        return status;
    }

    public OffsetDateTime getWonAt() {
        return wonAt;
    }

    public OffsetDateTime getLostAt() {
        return lostAt;
    }

    public String getLostReason() {
        return lostReason;
    }

    public OffsetDateTime getDeletedAt() {
        return deletedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setExpectedCloseDate(LocalDate expectedCloseDate) {
        this.expectedCloseDate = expectedCloseDate;
    }

    public void setOwnerId(UUID ownerId) {
        this.ownerId = ownerId;
    }

    public void setLeadId(UUID leadId) {
        this.leadId = leadId;
    }

    public void moveToStage(UUID stageId) {
        requireOpen();
        this.stageId = stageId;
    }

    /** Manueller Schätzbetrag, nur solange keine Positionen existieren (E-14). */
    public void estimateAmount(BigDecimal estimate) {
        requireOpen();
        this.amount = estimate;
        this.isEstimated = true;
    }

    /** Positionssumme übernimmt: Schätzung ist damit beendet (E-14). */
    public void recalculateFromItems(BigDecimal itemSum) {
        this.amount = itemSum;
        this.isEstimated = false;
    }

    public void win(UUID wonStageId) {
        requireOpen();
        this.status = Status.WON;
        this.stageId = wonStageId;
        this.wonAt = OffsetDateTime.now();
    }

    public void lose(UUID lostStageId, String reason) {
        requireOpen();
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("lost_reason ist beim Verlieren Pflicht");
        }
        this.status = Status.LOST;
        this.stageId = lostStageId;
        this.lostAt = OffsetDateTime.now();
        this.lostReason = reason;
    }

    public void softDelete() {
        this.deletedAt = OffsetDateTime.now();
    }

    private void requireOpen() {
        if (status != Status.OPEN) {
            throw new IllegalStateException("Opportunity ist bereits " + status);
        }
    }
}
