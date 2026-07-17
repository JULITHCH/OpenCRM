package de.julith.opencrm.importexport;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "export_jobs")
public class ExportJob {

    public enum Format { CSV, XLSX, JSON }

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false)
    private ImportJob.EntityType entityType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Format format;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> filter = new java.util.HashMap<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ImportJob.Status status = ImportJob.Status.PENDING;

    @Column(name = "file_path")
    private String filePath;

    @Column(name = "row_count")
    private Integer rowCount;

    @Column(name = "download_expires_at")
    private OffsetDateTime downloadExpiresAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "finished_at")
    private OffsetDateTime finishedAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected ExportJob() {
    }

    public ExportJob(UUID tenantId, ImportJob.EntityType entityType, Format format, Map<String, Object> filter,
                     UUID createdBy) {
        this.tenantId = tenantId;
        this.entityType = entityType;
        this.format = format;
        this.filter = filter != null ? filter : new java.util.HashMap<>();
        this.createdBy = createdBy;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public ImportJob.EntityType getEntityType() {
        return entityType;
    }

    public Format getFormat() {
        return format;
    }

    public Map<String, Object> getFilter() {
        return filter;
    }

    public ImportJob.Status getStatus() {
        return status;
    }

    public String getFilePath() {
        return filePath;
    }

    public Integer getRowCount() {
        return rowCount;
    }

    public OffsetDateTime getDownloadExpiresAt() {
        return downloadExpiresAt;
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

    public void start() {
        this.status = ImportJob.Status.RUNNING;
        this.startedAt = OffsetDateTime.now();
    }

    public void finish(String filePath, int rowCount) {
        this.filePath = filePath;
        this.rowCount = rowCount;
        this.finishedAt = OffsetDateTime.now();
        this.downloadExpiresAt = OffsetDateTime.now().plusHours(24);
        this.status = ImportJob.Status.COMPLETED;
    }

    public void fail() {
        this.finishedAt = OffsetDateTime.now();
        this.status = ImportJob.Status.FAILED;
    }
}
