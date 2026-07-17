package de.julith.opencrm.shared.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Setzt request_id/trace_id sehr früh in den Log-MDC (docs/11 Abschnitt 8) und gibt die
 * request_id als Header zurück, damit Support-Fälle über die ID nachvollziehbar sind.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = request.getHeader(HEADER);
        if (requestId == null || requestId.isBlank() || requestId.length() > 64) {
            requestId = UUID.randomUUID().toString();
        }
        MDC.put("request_id", requestId);
        MDC.put("trace_id", requestId);
        response.setHeader(HEADER, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove("request_id");
            MDC.remove("trace_id");
        }
    }
}
