package de.julith.opencrm.importexport;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "import_job_errors")
public class ImportJobError {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "import_job_id", nullable = false)
    private UUID importJobId;

    @Column(name = "row_number", nullable = false)
    private int rowNumber;

    @Column(name = "column_name")
    private String columnName;

    @Column(name = "error_code", nullable = false)
    private String errorCode;

    @Column(nullable = false)
    private String message;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_row")
    private Map<String, String> rawRow;

    protected ImportJobError() {
    }

    public ImportJobError(UUID importJobId, int rowNumber, String columnName, String errorCode, String message,
                          Map<String, String> rawRow) {
        this.importJobId = importJobId;
        this.rowNumber = rowNumber;
        this.columnName = columnName;
        this.errorCode = errorCode;
        this.message = message;
        this.rawRow = rawRow;
    }

    public UUID getId() {
        return id;
    }

    public UUID getImportJobId() {
        return importJobId;
    }

    public int getRowNumber() {
        return rowNumber;
    }

    public String getColumnName() {
        return columnName;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getMessage() {
        return message;
    }

    public Map<String, String> getRawRow() {
        return rawRow;
    }
}
