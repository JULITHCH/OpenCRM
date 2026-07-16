package de.julith.opencrm.importexport;

import de.julith.opencrm.shared.storage.FileStorage;
import de.julith.opencrm.shared.tenancy.TenantScopedExecutor;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Asynchroner, chunk-basierter Import-Läufer. Verarbeitet die Datei in Blöcken zu
 * {@value #CHUNK_SIZE} Zeilen; jeder Block läuft in einer eigenen Transaktion mit explizit
 * gesetztem Tenant-Kontext (RLS). Fortschritt und Fehler werden je Block persistiert, damit
 * der Job jederzeit beobachtbar ist und nach Absturz sauber neu gestartet werden kann.
 * Hinweis zu ADR-006: bewusst schlanker Runner auf Basis der import_jobs-Tabelle;
 * Wechsel auf das Spring-Batch-Framework, sobald Skip-/Restart-Semantik darüber hinauswächst.
 */
@Component
public class ImportRunner {

    static final int CHUNK_SIZE = 500;
    static final int MAX_ROWS = 100_000;
    static final int MAX_ERRORS_PERSISTED = 1_000;

    private static final Logger log = LoggerFactory.getLogger(ImportRunner.class);

    private final ImportJobRepository importJobRepository;
    private final ImportJobErrorRepository importJobErrorRepository;
    private final FileStorage fileStorage;
    private final TenantScopedExecutor tenantScopedExecutor;
    private final Map<ImportJob.EntityType, EntityRowImporter> importers;

    public ImportRunner(ImportJobRepository importJobRepository, ImportJobErrorRepository importJobErrorRepository,
                        FileStorage fileStorage, TenantScopedExecutor tenantScopedExecutor,
                        List<EntityRowImporter> importerList) {
        this.importJobRepository = importJobRepository;
        this.importJobErrorRepository = importJobErrorRepository;
        this.fileStorage = fileStorage;
        this.tenantScopedExecutor = tenantScopedExecutor;
        this.importers = new java.util.EnumMap<>(ImportJob.EntityType.class);
        importerList.forEach(importer -> importers.put(importer.entityType(), importer));
    }

    @Async("importExecutor")
    public void run(UUID jobId, UUID tenantId) {
        try {
            ImportJob job = tenantScopedExecutor.callAs(tenantId, () -> {
                ImportJob j = importJobRepository.findById(jobId)
                        .orElseThrow(() -> new NoSuchElementException("Import-Job " + jobId + " nicht gefunden"));
                importJobErrorRepository.deleteByImportJobId(jobId);
                j.start(j.getMode() == ImportJob.Mode.DRY_RUN ? ImportJob.Status.VALIDATING : ImportJob.Status.RUNNING);
                return importJobRepository.save(j);
            });
            process(job, tenantId);
        } catch (Exception e) {
            log.error("Import-Job {} fehlgeschlagen", jobId, e);
            tenantScopedExecutor.runAs(tenantId, () ->
                    importJobRepository.findById(jobId).ifPresent(j -> {
                        j.fail();
                        importJobRepository.save(j);
                    }));
        }
    }

    private void process(ImportJob job, UUID tenantId) throws IOException {
        EntityRowImporter importer = importers.get(job.getEntityType());
        if (importer == null) {
            throw new IllegalStateException("Kein Importer fuer " + job.getEntityType());
        }
        boolean execute = job.getMode() == ImportJob.Mode.EXECUTE;
        EntityRowImporter.DuplicateStrategy strategy = strategy(job);
        UUID defaultOwnerId = defaultOwnerId(job);
        Charset charset = charset(job);

        int total = 0;
        int skipped = 0;
        List<MappedRow> chunk = new ArrayList<>(CHUNK_SIZE);
        try (Reader reader = new InputStreamReader(fileStorage.get(job.getStorageKey()), charset);
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader().setSkipHeaderRecord(true).setTrim(true).setIgnoreEmptyLines(true)
                     .build().parse(reader)) {
            for (CSVRecord record : parser) {
                total++;
                if (total > MAX_ROWS) {
                    throw new IllegalStateException("Zeilenlimit von " + MAX_ROWS + " ueberschritten");
                }
                chunk.add(new MappedRow((int) record.getRecordNumber(), mapRow(job, record)));
                if (chunk.size() >= CHUNK_SIZE) {
                    skipped += flushChunk(job, tenantId, importer, chunk, execute, strategy, defaultOwnerId);
                    chunk = new ArrayList<>(CHUNK_SIZE);
                }
            }
        }
        if (!chunk.isEmpty()) {
            skipped += flushChunk(job, tenantId, importer, chunk, execute, strategy, defaultOwnerId);
        }

        int finalTotal = total;
        int finalSkipped = skipped;
        tenantScopedExecutor.runAs(tenantId, () ->
                importJobRepository.findById(job.getId()).ifPresent(j -> {
                    j.getOptions().put("skippedRows", finalSkipped);
                    j.finish(finalTotal);
                    importJobRepository.save(j);
                }));
    }

    /** Verarbeitet einen Block in eigener Transaktion; liefert die Anzahl übersprungener Duplikate. */
    private int flushChunk(ImportJob job, UUID tenantId, EntityRowImporter importer, List<MappedRow> chunk,
                           boolean execute, EntityRowImporter.DuplicateStrategy strategy, UUID defaultOwnerId) {
        return tenantScopedExecutor.callAs(tenantId, () -> {
            int errors = 0;
            int skipped = 0;
            long errorBudget = MAX_ERRORS_PERSISTED - importJobErrorRepository.countByImportJobId(job.getId());
            for (MappedRow row : chunk) {
                List<EntityRowImporter.RowError> rowErrors = importer.validate(row.values());
                if (!rowErrors.isEmpty()) {
                    errors++;
                    errorBudget -= persistErrors(job, row, rowErrors, errorBudget);
                    continue;
                }
                if (execute) {
                    if (importer.upsert(row.values(), strategy, defaultOwnerId)
                            == EntityRowImporter.RowResult.SKIPPED) {
                        skipped++;
                    }
                }
            }
            int finalErrors = errors;
            importJobRepository.findById(job.getId()).ifPresent(j -> {
                j.progress(chunk.size(), finalErrors);
                importJobRepository.save(j);
            });
            return skipped;
        });
    }

    /** Persistiert Zeilenfehler bis zum Budget-Limit; liefert die Anzahl geschriebener Einträge. */
    private int persistErrors(ImportJob job, MappedRow row, List<EntityRowImporter.RowError> rowErrors,
                              long errorBudget) {
        int written = 0;
        for (EntityRowImporter.RowError error : rowErrors) {
            if (written >= errorBudget) {
                break;
            }
            importJobErrorRepository.save(new ImportJobError(job.getId(), row.rowNumber(), error.column(),
                    error.code(), error.message(), row.values()));
            written++;
        }
        return written;
    }

    private static Map<String, String> mapRow(ImportJob job, CSVRecord record) {
        Map<String, String> mapped = new LinkedHashMap<>();
        job.getMapping().forEach((header, targetField) -> {
            if (record.isMapped(header)) {
                mapped.put(targetField, record.get(header));
            }
        });
        return mapped;
    }

    private static EntityRowImporter.DuplicateStrategy strategy(ImportJob job) {
        Object value = job.getOptions().get("duplicateStrategy");
        return value == null ? EntityRowImporter.DuplicateStrategy.SKIP
                : EntityRowImporter.DuplicateStrategy.valueOf(value.toString());
    }

    private static UUID defaultOwnerId(ImportJob job) {
        Object value = job.getOptions().get("defaultOwnerId");
        return value == null ? null : UUID.fromString(value.toString());
    }

    private static Charset charset(ImportJob job) {
        Object encoding = job.getOptions().get("encoding");
        if (encoding == null || "UTF-8".equalsIgnoreCase(encoding.toString())) {
            return StandardCharsets.UTF_8;
        }
        return Charset.forName(encoding.toString());
    }

    private record MappedRow(int rowNumber, Map<String, String> values) {
    }
}
