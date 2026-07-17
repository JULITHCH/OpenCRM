package de.julith.opencrm.shared.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.julith.opencrm.shared.web.ApiProblem;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Schreibt auch die von der Security-Filterkette erzeugten 401/403 als
 * application/problem+json (RFC 9457), damit Clients ein einheitliches Fehlerformat sehen.
 */
@Component
public class SecurityProblemHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public SecurityProblemHandlers(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        write(response, HttpStatus.UNAUTHORIZED, "unauthorized",
                "Authentifizierung erforderlich oder Token ungueltig");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        write(response, HttpStatus.FORBIDDEN, "forbidden", "Zugriff verweigert");
    }

    private void write(HttpServletResponse response, HttpStatus status, String code, String detail)
            throws IOException {
        ProblemDetail problem = ApiProblem.of(status, code, detail);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), problem);
    }
}
