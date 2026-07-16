package de.julith.opencrm.importexport;

import de.julith.opencrm.identity.User;
import de.julith.opencrm.identity.UserRepository;
import de.julith.opencrm.shared.storage.FileStorage;
import de.julith.opencrm.shared.tenancy.TenantContext;
import de.julith.opencrm.shared.web.QuotaExceededException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/export-jobs")
@PreAuthorize("hasAnyRole('tenant-admin', 'sales-manager', 'read-only')")
public class ExportController {

    static final int MAX_PARALLEL_PER_TENANT = 2;
    static final int MAX_PER_HOUR = 10;

    private final ExportJobRepository exportJobRepository;
    private final ExportRunner exportRunner;
    private final FileStorage fileStorage;
    private final UserRepository userRepository;

    public ExportController(ExportJobRepository exportJobRepository, ExportRunner exportRunner,
                            FileStorage fileStorage, UserRepository userRepository) {
        this.exportJobRepository = exportJobRepository;
        this.exportRunner = exportRunner;
        this.fileStorage = fileStorage;
        this.userRepository = userRepository;
    }

    public record ExportCreateRequest(@NotNull ImportJob.EntityType entityType, @NotNull ExportJob.Format format,
                                      Map<String, Object> filter) {
    }

    public record ExportResponse(UUID id, String entityType, String format, String status, Integer rowCount,
                                 String downloadExpiresAt, String createdAt) {
        static ExportResponse from(ExportJob job) {
            return new ExportResponse(job.getId(), job.getEntityType().name(), job.getFormat().name(),
                    job.getStatus().name(), job.getRowCount(),
                    job.getDownloadExpiresAt() != null ? job.getDownloadExpiresAt().toString() : null,
                    job.getCreatedAt() != null ? job.getCreatedAt().toString() : null);
        }
    }

    @PostMapping
    public ResponseEntity<ExportResponse> create(@Valid @RequestBody ExportCreateRequest request,
                                                 @AuthenticationPrincipal Jwt jwt) {
        ExportJob job = createJob(request, jwt);
        exportRunner.run(job.getId(), job.getTenantId());
        return ResponseEntity.created(URI.create("/api/v1/export-jobs/" + job.getId()))
                .body(ExportResponse.from(job));
    }

    @Transactional
    ExportJob createJob(ExportCreateRequest request, Jwt jwt) {
        // Tenant-Limits (E-35): max. 2 parallele Jobs, max. 10 pro Stunde
        if (exportJobRepository.countActive() >= MAX_PARALLEL_PER_TENANT) {
            throw new QuotaExceededException(
                    "Maximal " + MAX_PARALLEL_PER_TENANT + " parallele Export-Jobs je Mandant", 60);
        }
        if (exportJobRepository.countCreatedSince(OffsetDateTime.now().minusHours(1)) >= MAX_PER_HOUR) {
            throw new QuotaExceededException("Maximal " + MAX_PER_HOUR + " Export-Jobs pro Stunde je Mandant", 3600);
        }
        UUID actorId = userRepository.findByKeycloakId(jwt.getSubject()).map(User::getId).orElse(null);
        return exportJobRepository.save(new ExportJob(TenantContext.get(), request.entityType(),
                request.format(), request.filter(), actorId));
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<ExportResponse> list(@RequestParam(required = false, defaultValue = "20") int limit) {
        return exportJobRepository.findByOrderByCreatedAtDesc(PageRequest.ofSize(Math.min(limit, 100)))
                .stream().map(ExportResponse::from).toList();
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ExportResponse get(@PathVariable UUID id) {
        return ExportResponse.from(load(id));
    }

    @GetMapping("/{id}/download")
    @Transactional(readOnly = true)
    public ResponseEntity<InputStreamResource> download(@PathVariable UUID id) {
        ExportJob job = load(id);
        if (job.getFilePath() == null || job.getStatus() != ImportJob.Status.COMPLETED) {
            throw new IllegalStateException("Export ist noch nicht abgeschlossen");
        }
        if (job.getDownloadExpiresAt() != null && job.getDownloadExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new NoSuchElementException("Download-Link ist abgelaufen");
        }
        String fileName = "opencrm-" + job.getEntityType().name().toLowerCase() + "-export."
                + job.getFormat().name().toLowerCase();
        MediaType mediaType = switch (job.getFormat()) {
            case CSV -> MediaType.parseMediaType("text/csv");
            case XLSX -> MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            case JSON -> MediaType.APPLICATION_JSON;
        };
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header("Content-Disposition", "attachment; filename=\"" + fileName + "\"")
                .body(new InputStreamResource(fileStorage.get(job.getFilePath())));
    }

    private ExportJob load(UUID id) {
        return exportJobRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Export-Job " + id + " nicht gefunden"));
    }
}
