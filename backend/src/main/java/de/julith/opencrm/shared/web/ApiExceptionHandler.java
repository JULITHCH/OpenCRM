package de.julith.opencrm.shared.web;

import java.net.URI;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Fehlerformat RFC 9457 (docs/10-api-design.md). Validierungsfehler behandelt
 * Spring über spring.mvc.problemdetails.enabled=true selbst.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final String ERROR_NS = "https://opencrm.example/errors/";

    @ExceptionHandler(NoSuchElementException.class)
    ProblemDetail notFound(NoSuchElementException e) {
        return problem(HttpStatus.NOT_FOUND, "not_found", e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    ProblemDetail invalidState(IllegalStateException e) {
        return problem(HttpStatus.CONFLICT, "invalid_state_transition", e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail badRequest(IllegalArgumentException e) {
        return problem(HttpStatus.BAD_REQUEST, "validation_failed", e.getMessage());
    }

    private static ProblemDetail problem(HttpStatus status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(ERROR_NS + code));
        problem.setTitle(code);
        return problem;
    }
}
