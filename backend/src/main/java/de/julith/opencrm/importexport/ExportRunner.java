package de.julith.opencrm.importexport;

import de.julith.opencrm.shared.audit.AuditService;
import de.julith.opencrm.shared.storage.FileStorage;
import de.julith.opencrm.shared.tenancy.TenantScopedExecutor;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Asynchroner Export-Läufer: liest die Entitätsdaten unter RLS-Kontext und legt die Datei
 * im Objekt-Storage ab (Download über den Controller, Ablauf nach 24 h — E-35/E-36).
 * Formel-Injektion in CSV/XLSX wird durch Prefix-Escaping verhindert (docs/08 Sicherheit).
 */
@Component
public class ExportRunner {

    private static final Logger log = LoggerFactory.getLogger(ExportRunner.class);

    /** Export-Spalten je Entität (Reihenfolge = Dateispalten). */
    private static final Map<ImportJob.EntityType, ExportSpec> SPECS = Map.of(
            ImportJob.EntityType.LEAD, new ExportSpec(
                    "SELECT title, company_name, first_name, last_name, email, phone, source, status, score, "
                            + "external_id, created_at FROM leads WHERE deleted_at IS NULL ORDER BY created_at",
                    List.of("title", "companyName", "firstName", "lastName", "email", "phone", "source", "status",
                            "score", "externalId", "createdAt")),
            ImportJob.EntityType.ACCOUNT, new ExportSpec(
                    "SELECT name, industry, website, street, postal_code, city, country, external_id, created_at "
                            + "FROM accounts WHERE deleted_at IS NULL ORDER BY created_at",
                    List.of("name", "industry", "website", "street", "postalCode", "city", "country", "externalId",
                            "createdAt")),
            ImportJob.EntityType.CONTACT, new ExportSpec(
                    "SELECT last_name, first_name, email, phone, position, account_id, external_id, created_at "
                            + "FROM contacts WHERE deleted_at IS NULL ORDER BY created_at",
                    List.of("lastName", "firstName", "email", "phone", "position", "accountId", "externalId",
                            "createdAt")),
            ImportJob.EntityType.PRODUCT, new ExportSpec(
                    "SELECT sku, name, description, category, unit, list_price, currency, tax_rate, active, "
                            + "external_id, created_at FROM products WHERE deleted_at IS NULL ORDER BY created_at",
                    List.of("sku", "name", "description", "category", "unit", "listPrice", "currency", "taxRate",
                            "active", "externalId", "createdAt")));

    private record ExportSpec(String sql, List<String> columns) {
    }

    private final ExportJobRepository exportJobRepository;
    private final FileStorage fileStorage;
    private final TenantScopedExecutor tenantScopedExecutor;
    private final JdbcTemplate jdbcTemplate;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private final AuditService auditService;

    public ExportRunner(ExportJobRepository exportJobRepository, FileStorage fileStorage,
                        TenantScopedExecutor tenantScopedExecutor, JdbcTemplate jdbcTemplate,
                        com.fasterxml.jackson.databind.ObjectMapper objectMapper, AuditService auditService) {
        this.exportJobRepository = exportJobRepository;
        this.fileStorage = fileStorage;
        this.tenantScopedExecutor = tenantScopedExecutor;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.auditService = auditService;
    }

    @Async("importExecutor")
    public void run(UUID jobId, UUID tenantId) {
        try {
            // (1) Kurze Transaktion: Status RUNNING setzen und committen, damit RUNNING beobachtbar
            //     ist und keine lange Transaktion + DB-Connection ueber den ganzen Export haengt.
            ExportJob job = tenantScopedExecutor.callAs(tenantId, () -> {
                ExportJob j = exportJobRepository.findById(jobId)
                        .orElseThrow(() -> new NoSuchElementException("Export-Job " + jobId + " nicht gefunden"));
                j.start();
                return exportJobRepository.saveAndFlush(j);
            });
            ImportJob.EntityType entityType = job.getEntityType();
            ExportJob.Format format = job.getFormat();
            UUID createdBy = job.getCreatedBy();

            // (2a) Zeilen unter Tenant-Kontext + RLS in einem eigenen kurzen Aufruf lesen.
            List<Map<String, Object>> rows = tenantScopedExecutor.callAs(tenantId, () -> readRows(entityType));

            // (2b) Rendern und Storage-Put bewusst OHNE offene DB-Transaktion (kein FileStorage.put
            //      innerhalb einer DB-Transaktion, keine lange gehaltene Connection).
            byte[] content;
            try {
                content = render(entityType, format, rows);
            } catch (Exception e) {
                throw new IllegalStateException("Rendern des Exports fehlgeschlagen", e);
            }
            String key = tenantId + "/exports/" + jobId + "." + format.name().toLowerCase();
            fileStorage.put(key, new ByteArrayInputStream(content), content.length);

            int rowCount = rows.size();
            // (3) Kurze Transaktion: finish() mit file_path/row_count, danach Audit.
            tenantScopedExecutor.runAs(tenantId, () -> {
                ExportJob j = exportJobRepository.findById(jobId)
                        .orElseThrow(() -> new NoSuchElementException("Export-Job " + jobId + " nicht gefunden"));
                j.finish(key, rowCount);
                exportJobRepository.save(j);
                auditService.record("EXPORT", entityType.name(), jobId, createdBy,
                        Map.of("rows", rowCount, "format", format.name()));
            });
        } catch (Exception e) {
            log.error("Export-Job {} fehlgeschlagen", jobId, e);
            tenantScopedExecutor.runAs(tenantId, () ->
                    exportJobRepository.findById(jobId).ifPresent(j -> {
                        j.fail();
                        exportJobRepository.save(j);
                    }));
        }
    }

    private List<Map<String, Object>> readRows(ImportJob.EntityType entityType) {
        ExportSpec spec = SPECS.get(entityType);
        List<String> columns = spec.columns();
        return jdbcTemplate.query(spec.sql(), (rs, rowNum) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 0; i < columns.size(); i++) {
                row.put(columns.get(i), rs.getObject(i + 1));
            }
            return row;
        });
    }

    private byte[] render(ImportJob.EntityType entityType, ExportJob.Format format,
                          List<Map<String, Object>> rows) throws Exception {
        List<String> columns = SPECS.get(entityType).columns();
        return switch (format) {
            case CSV -> renderCsv(columns, rows);
            case XLSX -> renderXlsx(columns, rows);
            case JSON -> objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsBytes(rows.stream().map(this::stringifyValues).toList());
        };
    }

    private byte[] renderCsv(List<String> columns, List<Map<String, Object>> rows) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (CSVPrinter printer = new CSVPrinter(new OutputStreamWriter(out, StandardCharsets.UTF_8),
                CSVFormat.DEFAULT.builder().setHeader(columns.toArray(String[]::new)).build())) {
            for (Map<String, Object> row : rows) {
                printer.printRecord(columns.stream().map(c -> escapeFormula(stringify(row.get(c)))).toList());
            }
        }
        return out.toByteArray();
    }

    private byte[] renderXlsx(List<String> columns, List<Map<String, Object>> rows) throws Exception {
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100)) {
            var sheet = workbook.createSheet("Export");
            var header = sheet.createRow(0);
            for (int i = 0; i < columns.size(); i++) {
                header.createCell(i).setCellValue(columns.get(i));
            }
            int rowIndex = 1;
            for (Map<String, Object> row : rows) {
                var sheetRow = sheet.createRow(rowIndex++);
                for (int i = 0; i < columns.size(); i++) {
                    sheetRow.createCell(i).setCellValue(escapeFormula(stringify(row.get(columns.get(i)))));
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            workbook.dispose();
            return out.toByteArray();
        }
    }

    private Map<String, Object> stringifyValues(Map<String, Object> row) {
        Map<String, Object> result = new LinkedHashMap<>();
        row.forEach((key, value) -> result.put(key, value == null ? null : stringify(value)));
        return result;
    }

    private static String stringify(Object value) {
        return value == null ? "" : value.toString();
    }

    /** Prefix-Escaping gegen CSV-/XLSX-Formel-Injektion (docs/08 Abschnitt Sicherheit). */
    private static String escapeFormula(String value) {
        if (!value.isEmpty() && (value.charAt(0) == '=' || value.charAt(0) == '+'
                || value.charAt(0) == '-' || value.charAt(0) == '@')) {
            return "'" + value;
        }
        return value;
    }
}
