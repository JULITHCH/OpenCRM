package de.julith.opencrm.shared.web;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.UUID;

/**
 * Cursor-Kodierung für Keyset-Pagination (docs/10-api-design.md): sortiert wird
 * nach (created_at DESC, id DESC); der Cursor transportiert das letzte Wertepaar.
 */
public final class Cursors {

    public record Cursor(OffsetDateTime createdAt, UUID id) {
    }

    private Cursors() {
    }

    public static String encode(OffsetDateTime createdAt, UUID id) {
        String raw = createdAt.toString() + "|" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static Cursor decode(String cursor) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int separator = raw.lastIndexOf('|');
            return new Cursor(OffsetDateTime.parse(raw.substring(0, separator)),
                    UUID.fromString(raw.substring(separator + 1)));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Ungueltiger cursor-Parameter", e);
        }
    }

    public static int clampLimit(Integer limit) {
        if (limit == null) {
            return 50;
        }
        return Math.max(1, Math.min(limit, 200));
    }
}
