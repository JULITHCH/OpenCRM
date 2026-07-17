package de.julith.opencrm.shared.web;

import de.julith.opencrm.shared.tenancy.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Übernimmt den tenant_id-Claim aus dem validierten JWT in den TenantContext (RLS)
 * und spiegelt tenant_id/user_id in den Log-MDC (docs/11 Abschnitt 8, nur UUIDs, keine PII).
 * Räumt beides nach dem Request garantiert wieder ab.
 */
@Component
public class TenantContextFilter extends OncePerRequestFilter {

    public static final String TENANT_CLAIM = "tenant_id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication instanceof JwtAuthenticationToken jwtAuth) {
                String claim = jwtAuth.getToken().getClaimAsString(TENANT_CLAIM);
                if (claim != null && !claim.isBlank()) {
                    TenantContext.set(UUID.fromString(claim));
                    MDC.put("tenant_id", claim);
                }
                // user_id im Log = Keycloak-Subject (UUID, keine PII)
                MDC.put("user_id", jwtAuth.getToken().getSubject());
            }
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            MDC.remove("tenant_id");
            MDC.remove("user_id");
        }
    }
}
