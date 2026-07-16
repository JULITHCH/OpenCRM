package de.julith.opencrm.importexport;

import de.julith.opencrm.identity.User;
import de.julith.opencrm.identity.UserRepository;
import de.julith.opencrm.shared.storage.FileStorage;
import de.julith.opencrm.shared.tenancy.TenantContext;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ImportService {

    static final long MAX_FILE_BYTES = 50L * 1024 * 1024;

    /** Header-Synonyme je Zielfeld für den Mapping-Vorschlag (normalisiert verglichen). */
    private static final Map<String, List<String>> LEAD_SYNONYMS = Map.of(
            "title", List.of("title", "titel", "betreff", "anfrage"),
            "companyName", List.of("companyname", "company", "firma", "unternehmen", "firmenname"),
            "firstName", List.of("firstname", "vorname"),
            "lastName", List.of("lastname", "nachname", "name"),
            "email", List.of("email", "e-mail", "mail", "emailadresse"),
            "phone", List.of("phone", "telefon", "tel", "telefonnummer"),
            "source", List.of("source", "quelle", "herkunft"),
            "score", List.of("score", "punkte", "bewertung"),
            "externalId", List.of("externalid", "externeid", "external_id", "id", "kundennummer"));

    private final ImportJobRepository importJobRepository;
    private final FileStorage fileStorage;
    private final UserRepository userRepository;
    private final Map<ImportJob.EntityType, EntityRowImporter> importers;

    public ImportService(ImportJobRepository importJobRepository, FileStorage fileStorage,
                         UserRepository userRepository, List<EntityRowImporter> importerList) {
        this.importJobRepository = importJobRepository;
        this.fileStorage = fileStorage;
        this.userRepository = userRepository;
        this.importers = new java.util.EnumMap<>(ImportJob.EntityType.class);
        importerList.forEach(importer -> importers.put(importer.entityType(), importer));
    }

    @Transactional
    public ImportJob upload(MultipartFile file, ImportJob.EntityType entityType, String actorKeycloakId) {
        if (!importers.containsKey(entityType)) {
            throw new IllegalArgumentException(
                    "Entitaetstyp " + entityType + " wird fuer den Import noch nicht unterstuetzt");
        }
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Datei ist leer");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new IllegalArgumentException("Datei ueberschreitet das Limit von 50 MB");
        }

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Upload nicht lesbar", e);
        }
        List<String> headers = readHeaders(content);

        UUID tenantId = TenantContext.get();
        String storageKey = tenantId + "/imports/" + UUID.randomUUID() + ".csv";
        fileStorage.put(storageKey, new ByteArrayInputStream(content), content.length);

        UUID actorId = userRepository.findByKeycloakId(actorKeycloakId).map(User::getId).orElse(null);
        ImportJob job = new ImportJob(tenantId, entityType,
                file.getOriginalFilename() != null ? file.getOriginalFilename() : "import.csv",
                storageKey, ImportJob.Format.CSV, actorId);
        job.getOptions().put("headers", headers);
        return importJobRepository.save(job);
    }

    @Transactional(readOnly = true)
    public Map<String, String> suggestMapping(UUID jobId) {
        ImportJob job = load(jobId);
        @SuppressWarnings("unchecked")
        List<String> headers = (List<String>) job.getOptions().getOrDefault("headers", List.of());
        Map<String, String> suggestion = new LinkedHashMap<>();
        for (String header : headers) {
            String normalized = normalize(header);
            LEAD_SYNONYMS.forEach((target, synonyms) -> {
                if (synonyms.contains(normalized) && !suggestion.containsValue(target)) {
                    suggestion.put(header, target);
                }
            });
        }
        return suggestion;
    }

    @Transactional
    public ImportJob startRun(UUID jobId, Map<String, String> mapping, Map<String, Object> options,
                              ImportJob.Mode mode) {
        ImportJob job = load(jobId);
        Map<String, String> effectiveMapping = mapping != null && !mapping.isEmpty() ? mapping : job.getMapping();
        if (effectiveMapping.isEmpty()) {
            throw new IllegalArgumentException("Es ist kein Spalten-Mapping gesetzt");
        }
        validateMapping(job, effectiveMapping);
        if (options != null && options.get("defaultOwnerId") != null) {
            UUID ownerId = UUID.fromString(options.get("defaultOwnerId").toString());
            userRepository.findById(ownerId).filter(User::isActive).orElseThrow(
                    () -> new NoSuchElementException("Default-Owner " + ownerId + " nicht gefunden oder inaktiv"));
        }
        job.configure(effectiveMapping, options != null ? options : Map.of(), mode);
        // Der asynchrone Lauf wird vom Controller NACH dem Commit dieser Transaktion gestartet,
        // damit der Runner garantiert den konfigurierten Zustand liest
        return importJobRepository.save(job);
    }

    private void validateMapping(ImportJob job, Map<String, String> mapping) {
        List<String> allowed = importers.get(job.getEntityType()).targetFields();
        for (String target : mapping.values()) {
            if (!allowed.contains(target)) {
                throw new IllegalArgumentException("Unbekanntes Zielfeld im Mapping: " + target);
            }
        }
    }

    private ImportJob load(UUID jobId) {
        return importJobRepository.findById(jobId)
                .orElseThrow(() -> new NoSuchElementException("Import-Job " + jobId + " nicht gefunden"));
    }

    private static List<String> readHeaders(byte[] content) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream(content), StandardCharsets.UTF_8));
             CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setTrim(true)
                     .build().parse(reader)) {
            return List.copyOf(parser.getHeaderNames());
        } catch (IOException | IllegalArgumentException e) {
            throw new IllegalArgumentException("CSV-Kopfzeile konnte nicht gelesen werden", e);
        }
    }

    private static String normalize(String header) {
        return header.toLowerCase(Locale.ROOT)
                .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
                .replaceAll("[\\s_-]", "");
    }
}
