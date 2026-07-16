package de.julith.opencrm.importexport;

import de.julith.opencrm.shared.storage.FileStorage;
import de.julith.opencrm.shared.tenancy.TenantScopedExecutor;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Speicher-Bereinigung (E-69): entfernt nachts je Tenant abgelaufene Export-Dateien
 * und alte Import-Artefakte. Die DB-Zeilen bleiben fuer Kennzahlen/Audit erhalten;
 * geloescht werden nur die Nutzdateien im Storage und alte Fehlerzeilen.
 */
@Component
public class StorageCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(StorageCleanupJob.class);

    private final JdbcTemplate jdbcTemplate;
    private final TenantScopedExecutor tenantScopedExecutor;
    private final FileStorage fileStorage;

    public StorageCleanupJob(JdbcTemplate jdbcTemplate, TenantScopedExecutor tenantScopedExecutor,
                             FileStorage fileStorage) {
        this.jdbcTemplate = jdbcTemplate;
        this.tenantScopedExecutor = tenantScopedExecutor;
        this.fileStorage = fileStorage;
    }

    @Scheduled(cron = "0 0 3 * * *")
    @SchedulerLock(name = "storage_cleanup", lockAtMostFor = "1h")
    public void cleanupAllTenants() {
        List<UUID> tenants = jdbcTemplate.query("SELECT id FROM tenants WHERE status = 'ACTIVE'",
                (rs, rowNum) -> rs.getObject(1, UUID.class));
        for (UUID tenantId : tenants) {
            try {
                tenantScopedExecutor.runAs(tenantId, this::cleanupCurrentTenant);
            } catch (Exception e) {
                log.error("Speicher-Bereinigung fuer Tenant {} fehlgeschlagen", tenantId, e);
            }
        }
    }

    /** Läuft unter gesetztem Tenant-Kontext (auch aus Tests direkt aufrufbar). */
    public void cleanupCurrentTenant() {
        int clearedExports = clearExpiredExportFiles();
        int deletedImports = deleteOldImportFiles();
        int deletedErrorRows = jdbcTemplate.update("""
                DELETE FROM import_job_errors
                WHERE import_job_id IN (
                    SELECT id FROM import_jobs
                    WHERE created_at < now() - interval '90 days'
                )
                """);

        if (clearedExports > 0 || deletedImports > 0 || deletedErrorRows > 0) {
            log.info("Speicher-Bereinigung: {} Export-Dateien, {} Import-Dateien, {} Import-Fehlerzeilen",
                    clearedExports, deletedImports, deletedErrorRows);
        }
    }

    /** Abgelaufene Export-Dateien loeschen und file_path leeren (Zeile bleibt erhalten). */
    private int clearExpiredExportFiles() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT id, file_path FROM export_jobs
                WHERE file_path IS NOT NULL AND download_expires_at < now()
                """);
        int cleared = 0;
        for (Map<String, Object> row : rows) {
            String filePath = (String) row.get("file_path");
            try {
                fileStorage.delete(filePath);
                jdbcTemplate.update("UPDATE export_jobs SET file_path = NULL WHERE id = ?", row.get("id"));
                cleared++;
            } catch (Exception e) {
                log.warn("Export-Datei {} konnte nicht geloescht werden", filePath, e);
            }
        }
        return cleared;
    }

    /** Import-Dateien aelter als 30 Tage aus dem Storage entfernen (storage_key bleibt bestehen). */
    private int deleteOldImportFiles() {
        List<String> keys = jdbcTemplate.queryForList("""
                SELECT storage_key FROM import_jobs
                WHERE storage_key IS NOT NULL
                  AND created_at < now() - interval '30 days'
                """, String.class);
        int deleted = 0;
        for (String storageKey : keys) {
            try {
                fileStorage.delete(storageKey);
                deleted++;
            } catch (Exception e) {
                log.warn("Import-Datei {} konnte nicht geloescht werden", storageKey, e);
            }
        }
        return deleted;
    }
}
