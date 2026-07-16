package de.julith.opencrm.importexport;

import java.util.List;
import java.util.Map;

/**
 * Import-Adapter je Entitätstyp: Validierung und Upsert einer gemappten Zeile.
 * Implementierungen laufen innerhalb der Chunk-Transaktion mit gesetztem Tenant-Kontext.
 */
public interface EntityRowImporter {

    enum RowResult { CREATED, UPDATED, SKIPPED }

    record RowError(String column, String code, String message) {
    }

    ImportJob.EntityType entityType();

    /** Erlaubte Zielfelder des Mappings. */
    List<String> targetFields();

    /** Prüft die gemappte Zeile (Zielfeld -> Wert); leere Liste = gültig. */
    List<RowError> validate(Map<String, String> row);

    /** Legt an bzw. aktualisiert gemäß Duplikatstrategie; nur im EXECUTE-Modus aufgerufen. */
    RowResult upsert(Map<String, String> row, DuplicateStrategy strategy, java.util.UUID defaultOwnerId);

    enum DuplicateStrategy { SKIP, UPDATE, CREATE }
}
