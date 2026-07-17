package de.julith.opencrm.shared.web;

import java.net.URI;
import org.slf4j.MDC;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

/**
 * Baut RFC-9457-Antworten mit den in docs/10-api-design.md zugesagten Erweiterungsfeldern
 * (code, traceId). type/title tragen den stabilen Fehlercode.
 */
public final class ApiProblem {

    static final String ERROR_NS = "https://opencrm.example/errors/";

    private ApiProblem() {
    }

    public static ProblemDetail of(HttpStatusCode status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(ERROR_NS + code));
        problem.setTitle(code);
        problem.setProperty("code", code);
        String traceId = MDC.get("trace_id");
        if (traceId == null) {
            traceId = MDC.get("request_id");
        }
        if (traceId != null) {
            problem.setProperty("traceId", traceId);
        }
        return problem;
    }
}
