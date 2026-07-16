package de.julith.opencrm.shared.web;

import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Fehlerformat RFC 9457 (docs/10-api-design.md). Bean-Validierungsfehler behandelt
 * Spring über spring.mvc.problemdetails.enabled=true selbst; hier die fachlichen Fälle.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(NoSuchElementException.class)
    ProblemDetail notFound(NoSuchElementException e) {
        return ApiProblem.of(HttpStatus.NOT_FOUND, "not_found", e.getMessage());
    }

    @ExceptionHandler(DuplicateException.class)
    ProblemDetail duplicate(DuplicateException e) {
        return ApiProblem.of(HttpStatus.CONFLICT, "duplicate_found", e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    ProblemDetail invalidState(IllegalStateException e) {
        return ApiProblem.of(HttpStatus.CONFLICT, "invalid_state_transition", e.getMessage());
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentTypeMismatchException.class})
    ProblemDetail badRequest(Exception e) {
        String detail = e instanceof MethodArgumentTypeMismatchException mismatch
                ? "Ungueltiger Parameter: " + mismatch.getName()
                : e.getMessage();
        return ApiProblem.of(HttpStatus.BAD_REQUEST, "validation_failed", detail);
    }

    /** @PreAuthorize/Ownership-Verstoesse aus Controllern/Services als 403 problem+json. */
    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail forbidden(AccessDeniedException e) {
        return ApiProblem.of(HttpStatus.FORBIDDEN, "forbidden",
                e.getMessage() != null ? e.getMessage() : "Zugriff verweigert");
    }

    @ExceptionHandler(QuotaExceededException.class)
    ResponseEntity<ProblemDetail> quotaExceeded(QuotaExceededException e) {
        ProblemDetail problem = ApiProblem.of(HttpStatus.TOO_MANY_REQUESTS, "quota_exceeded", e.getMessage());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(e.getRetryAfterSeconds()))
                .body(problem);
    }
}
