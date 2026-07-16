package de.julith.opencrm.tenant;

import de.julith.opencrm.identity.Team;
import de.julith.opencrm.identity.TeamRepository;
import de.julith.opencrm.shared.tenancy.TenantScopedExecutor;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Provisionierung eines Mandanten in einem Schritt (E-54): tenants-Zeile plus Seeds
 * (Default-Team; Default-Pipeline folgt mit dem sales-Modul in M2).
 * Das Anlegen der zugehörigen Keycloak-Organization ist als Folgeausbau vorgesehen
 * (docs/04-multi-tenancy.md, Abschnitt Provisionierung) — bis dahin pflegt der
 * platform-admin die Organization über die Keycloak-Admin-Konsole.
 */
@Service
public class TenantProvisioningService {

    public static final String DEFAULT_TEAM_NAME = "Vertrieb";

    private final TenantRepository tenantRepository;
    private final TeamRepository teamRepository;
    private final TenantScopedExecutor tenantScopedExecutor;

    public TenantProvisioningService(TenantRepository tenantRepository, TeamRepository teamRepository,
                                     TenantScopedExecutor tenantScopedExecutor) {
        this.tenantRepository = tenantRepository;
        this.teamRepository = teamRepository;
        this.tenantScopedExecutor = tenantScopedExecutor;
    }

    @Transactional
    public Tenant provision(String name, String slug) {
        String normalizedSlug = slug.trim().toLowerCase(Locale.ROOT);
        Tenant tenant = tenantRepository.save(new Tenant(UUID.randomUUID(), name.trim(), normalizedSlug));

        // Seeding läuft unter dem Kontext des NEUEN Tenants in eigener Transaktion,
        // da das Token des platform-admin keinen (bzw. einen fremden) tenant_id-Claim trägt
        tenantScopedExecutor.runAs(tenant.getId(),
                () -> teamRepository.save(new Team(tenant.getId(), DEFAULT_TEAM_NAME)));
        return tenant;
    }
}
