package de.julith.opencrm.importexport;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "import_jobs")
public class ImportJob {

    public enum EntityType { LEAD, ACCOUNT, CONTACT, PRODUCT }

    public enum Format { CSV, XLSX }

    public enum Mode { DRY_RUN, EXECUTE }

    public enum Status { PENDING, VALIDATING, RUNNING, COMPLETED, COMPLETED_WITH_ERRORS, FAILED, CANCELLED }

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false)
    private EntityType entityType;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "storage_key", nullable = false)
    private String storageKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Format format;

    /** Spalten-Mapping: CSV-Header -> Zielfeld (z. B. {"Firma": "companyName"}). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private java.util.Map<String, String> mapping = new java.util.HashMap<>();

    /** Optionen: duplicateStrategy, encoding, defaultOwnerId, headers (erkannte Spalten). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private java.util.Map<String, Object> options = new java.util.HashMap<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Mode mode = Mode.DRY_RUN;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.PENDING;

    @Column(name = "total_rows")
    private Integer totalRows;

    @Column(name = "processed_rows", nullable = false)
    private int processedRows;

    @Column(name = "error_rows", nullable = false)
    private int errorRows;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "finished_at")
    private OffsetDateTime finishedAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected ImportJob() {
    }

    public ImportJob(UUID tenantId, EntityType entityType, String fileName, String storageKey, Format format,
                     UUID createdBy) {
        this.tenantId = tenantId;
        this.entityType = entityType;
        this.fileName = fileName;
        this.storageKey = storageKey;
        this.format = format;
        this.createdBy = createdBy;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public EntityType getEntityType() {
        return entityType;
    }

    public String getFileName() {
        return fileName;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public Format getFormat() {
        return format;
    }

    public java.util.Map<String, String> getMapping() {
        return mapping;
    }

    public java.util.Map<String, Object> getOptions() {
        return options;
    }

    public Mode getMode() {
        return mode;
    }

    public Status getStatus() {
        return status;
    }

    public Integer getTotalRows() {
        return totalRows;
    }

    public int getProcessedRows() {
        return processedRows;
    }

    public int getErrorRows() {
        return errorRows;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public OffsetDateTime getStartedAt() {
        return startedAt;
    }

    public OffsetDateTime getFinishedAt() {
        return finishedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void configure(java.util.Map<String, String> mapping, java.util.Map<String, Object> options, Mode mode) {
        if (status != Status.PENDING && status != Status.COMPLETED && status != Status.COMPLETED_WITH_ERRORS
                && status != Status.FAILED) {
            throw new IllegalStateException("Job ist im Status " + status + " nicht konfigurierbar");
        }
        this.mapping = new java.util.HashMap<>(mapping);
        this.options.putAll(options);
        this.mode = mode;
        // Synchron zurueck auf PENDING: Clients pollen ab jetzt einen frischen Lauf,
        // nie den Endzustand des vorherigen Laufs (der asynchrone Runner startet erst danach)
        this.status = Status.PENDING;
        this.finishedAt = null;
    }

    public void start(Status runningStatus) {
        this.status = runningStatus;
        this.startedAt = OffsetDateTime.now();
        this.finishedAt = null;
        this.processedRows = 0;
        this.errorRows = 0;
    }

    public void progress(int processedDelta, int errorDelta) {
        this.processedRows += processedDelta;
        this.errorRows += errorDelta;
    }

    public void finish(int totalRows) {
        this.totalRows = totalRows;
        this.finishedAt = OffsetDateTime.now();
        this.status = errorRows > 0 ? Status.COMPLETED_WITH_ERRORS : Status.COMPLETED;
    }

    public void fail() {
        this.finishedAt = OffsetDateTime.now();
        this.status = Status.FAILED;
    }
}
