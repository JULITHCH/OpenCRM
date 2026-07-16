package de.julith.opencrm.importexport;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Gemeinsame Validierungs- und Normalisierungshelfer für Entity-Importer. */
public final class ImportSupport {

    public static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private ImportSupport() {
    }

    public static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static void checkEmail(Map<String, String> row, List<EntityRowImporter.RowError> errors) {
        String email = trimToNull(row.get("email"));
        if (email != null && !EMAIL.matcher(email).matches()) {
            errors.add(new EntityRowImporter.RowError("email", "INVALID_EMAIL",
                    "Ungueltiges E-Mail-Format: " + email));
        }
    }

    public static Integer parseIntOrError(Map<String, String> row, String field,
                                          List<EntityRowImporter.RowError> errors) {
        String raw = trimToNull(row.get(field));
        if (raw == null) {
            return null;
        }
        try {
            return Integer.valueOf(raw);
        } catch (NumberFormatException e) {
            errors.add(new EntityRowImporter.RowError(field, "INVALID_NUMBER",
                    "Keine gueltige Zahl: " + raw));
            return null;
        }
    }
}
