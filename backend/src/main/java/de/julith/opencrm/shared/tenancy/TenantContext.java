package de.julith.opencrm.shared.tenancy;

import java.util.UUID;

/**
 * Hält die Tenant-ID des aktuellen Ausführungskontexts (Request oder Batch-Job).
 * Quelle im Web-Pfad ist ausschließlich der JWT-Claim {@code tenant_id} — nie ein Client-Parameter.
 */
public final class TenantContext {

    /** Sentinel für "kein Tenant gesetzt" — RLS-Policies liefern dann keine Zeilen (fail-closed). */
    public static final String NONE = "none";

    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(UUID tenantId) {
        CURRENT.set(tenantId);
    }

    public static UUID get() {
        return CURRENT.get();
    }

    public static String getIdentifier() {
        UUID id = CURRENT.get();
        return id == null ? NONE : id.toString();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
