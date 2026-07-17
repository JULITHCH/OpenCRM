package de.julith.opencrm.identity;

import de.julith.opencrm.shared.tenancy.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Läuft als Servlet-Filter NACH der Security-Filterkette (und damit nach dem
 * TenantContextFilter): sorgt dafür, dass der angemeldete Nutzer als users-Zeile existiert.
 * Tokens ohne tenant_id (z. B. platform-admin, Service-Client) werden übersprungen.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class UserProvisioningFilter extends OncePerRequestFilter {

    private final UserProvisioningService userProvisioningService;

    public UserProvisioningFilter(UserProvisioningService userProvisioningService) {
        this.userProvisioningService = userProvisioningService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UUID tenantId = TenantContext.get();
        if (tenantId != null && authentication instanceof JwtAuthenticationToken jwtAuth) {
            try {
                userProvisioningService.ensureUser(jwtAuth.getToken(), tenantId);
            } catch (DataIntegrityViolationException e) {
                // Zwei parallele Erst-Requests desselben Nutzers: der Verlierer des
                // Unique-Constraints ignoriert den Konflikt, die Zeile existiert bereits
            }
        }
        filterChain.doFilter(request, response);
    }
}
