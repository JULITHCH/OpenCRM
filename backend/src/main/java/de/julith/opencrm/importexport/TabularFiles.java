package de.julith.opencrm.importexport;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/** Einheitliches Lesen tabellarischer Import-Dateien (CSV und XLSX, erste Kopfzeile = Header). */
public final class TabularFiles {

    public record RawRow(int rowNumber, Map<String, String> values) {
    }

    private TabularFiles() {
    }

    public static List<String> readHeaders(ImportJob.Format format, InputStream in, Charset charset)
            throws IOException {
        if (format == ImportJob.Format.XLSX) {
            List<RawRow> rows = readXlsx(in, 1);
            return rows.isEmpty() ? List.of() : List.copyOf(rows.getFirst().values().keySet());
        }
        try (Reader reader = new InputStreamReader(in, charset);
             CSVParser parser = csvFormat().parse(reader)) {
            return List.copyOf(parser.getHeaderNames());
        }
    }

    public static List<RawRow> readRows(ImportJob.Format format, InputStream in, Charset charset, int maxRows)
            throws IOException {
        if (format == ImportJob.Format.XLSX) {
            return readXlsx(in, maxRows);
        }
        List<RawRow> rows = new ArrayList<>();
        try (Reader reader = new InputStreamReader(in, charset);
             CSVParser parser = csvFormat().parse(reader)) {
            List<String> headers = parser.getHeaderNames();
            for (CSVRecord record : parser) {
                if (rows.size() >= maxRows) {
                    throw new IllegalStateException("Zeilenlimit von " + maxRows + " ueberschritten");
                }
                Map<String, String> values = new LinkedHashMap<>();
                for (String header : headers) {
                    values.put(header, record.isMapped(header) ? record.get(header) : null);
                }
                rows.add(new RawRow((int) record.getRecordNumber(), values));
            }
        }
        return rows;
    }

    private static CSVFormat csvFormat() {
        return CSVFormat.DEFAULT.builder()
                .setHeader().setSkipHeaderRecord(true).setTrim(true).setIgnoreEmptyLines(true)
                .build();
    }

    private static List<RawRow> readXlsx(InputStream in, int maxRows) throws IOException {
        List<RawRow> rows = new ArrayList<>();
        DataFormatter formatter = new DataFormatter();
        try (Workbook workbook = new XSSFWorkbook(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) {
                return rows;
            }
            List<String> headers = new ArrayList<>();
            headerRow.forEach(cell -> headers.add(formatter.formatCellValue(cell).trim()));

            for (int r = sheet.getFirstRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                if (rows.size() >= maxRows) {
                    throw new IllegalStateException("Zeilenlimit von " + maxRows + " ueberschritten");
                }
                Map<String, String> values = new LinkedHashMap<>();
                boolean hasContent = false;
                for (int c = 0; c < headers.size(); c++) {
                    String value = formatter.formatCellValue(row.getCell(c)).trim();
                    values.put(headers.get(c), value.isEmpty() ? null : value);
                    hasContent |= !value.isEmpty();
                }
                if (hasContent) {
                    rows.add(new RawRow(r + 1, values));
                }
            }
        }
        return rows;
    }
}
