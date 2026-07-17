package de.julith.opencrm.shared.storage;

import java.io.InputStream;

/**
 * Ablage für Import-/Exportdateien. M1 liefert die lokale Implementierung;
 * die S3/MinIO-Implementierung folgt mit den Export-Jobs (signierte URLs) in M2.
 */
public interface FileStorage {

    /** Legt den Inhalt unter dem Schlüssel ab (Schlüssel enthält den Tenant-Prefix). */
    void put(String key, InputStream content, long contentLength);

    InputStream get(String key);

    void delete(String key);
}
