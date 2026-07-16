package de.julith.opencrm.importexport;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/import-jobs")
@PreAuthorize("hasAnyRole('tenant-admin', 'sales-manager')")
public class ImportController {

    private final ImportService importService;
    private final ImportRunner importRunner;
    private final ImportJobRepository importJobRepository;
    private final ImportJobErrorRepository importJobErrorRepository;

    public ImportController(ImportService importService, ImportRunner importRunner,
                            ImportJobRepository importJobRepository,
                            ImportJobErrorRepository importJobErrorRepository) {
        this.importService = importService;
        this.importRunner = importRunner;
        this.importJobRepository = importJobRepository;
        this.importJobErrorRepository = importJobErrorRepository;
    }

    public record RunRequest(Map<String, String> mapping, Map<String, Object> options) {
    }

    public record JobResponse(UUID id, String entityType, String fileName, String format, String mode, String status,
                              Integer totalRows, int processedRows, int errorRows, Map<String, String> mapping,
                              Map<String, Object> options, String startedAt, String finishedAt, String createdAt) {
        static JobResponse from(ImportJob job) {
            return new JobResponse(job.getId(), job.getEntityType().name(), job.getFileName(),
                    job.getFormat().name(), job.getMode().name(), job.getStatus().name(), job.getTotalRows(),
                    job.getProcessedRows(), job.getErrorRows(), job.getMapping(), job.getOptions(),
                    job.getStartedAt() != null ? job.getStartedAt().toString() : null,
                    job.getFinishedAt() != null ? job.getFinishedAt().toString() : null,
                    job.getCreatedAt() != null ? job.getCreatedAt().toString() : null);
        }
    }

    public record ErrorResponse(int rowNumber, String columnName, String errorCode, String message,
                                Map<String, String> rawRow) {
        static ErrorResponse from(ImportJobError error) {
            return new ErrorResponse(error.getRowNumber(), error.getColumnName(), error.getErrorCode(),
                    error.getMessage(), error.getRawRow());
        }
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<JobResponse> upload(@RequestPart("file") MultipartFile file,
                                              @RequestParam("entityType") ImportJob.EntityType entityType,
                                              @AuthenticationPrincipal Jwt jwt) {
        ImportJob job = importService.upload(file, entityType, jwt.getSubject());
        return ResponseEntity.created(URI.create("/api/v1/import-jobs/" + job.getId()))
                .body(JobResponse.from(job));
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<JobResponse> list(@RequestParam(required = false, defaultValue = "20") int limit) {
        return importJobRepository.findByOrderByCreatedAtDesc(PageRequest.ofSize(Math.min(limit, 100)))
                .stream().map(JobResponse::from).toList();
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public JobResponse get(@PathVariable UUID id) {
        return JobResponse.from(load(id));
    }

    @GetMapping("/{id}/mapping-suggestion")
    public Map<String, String> mappingSuggestion(@PathVariable UUID id) {
        return importService.suggestMapping(id);
    }

    @PostMapping("/{id}/validate")
    public JobResponse validate(@PathVariable UUID id, @RequestBody RunRequest request) {
        ImportJob job = importService.startRun(id, request.mapping(), request.options(), ImportJob.Mode.DRY_RUN);
        importRunner.run(job.getId(), job.getTenantId());
        return JobResponse.from(job);
    }

    @PostMapping("/{id}/execute")
    public JobResponse execute(@PathVariable UUID id, @RequestBody(required = false) RunRequest request) {
        ImportJob job = importService.startRun(id,
                request != null ? request.mapping() : null,
                request != null ? request.options() : null,
                ImportJob.Mode.EXECUTE);
        importRunner.run(job.getId(), job.getTenantId());
        return JobResponse.from(job);
    }

    @GetMapping("/{id}/errors")
    @Transactional(readOnly = true)
    public List<ErrorResponse> errors(@PathVariable UUID id,
                                      @RequestParam(required = false, defaultValue = "100") int limit) {
        load(id);
        return importJobErrorRepository.findByImportJobIdOrderByRowNumber(id, PageRequest.ofSize(Math.min(limit, 1000)))
                .stream().map(ErrorResponse::from).toList();
    }

    private ImportJob load(UUID id) {
        return importJobRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Import-Job " + id + " nicht gefunden"));
    }
}
