package de.julith.opencrm.tenant;

import de.julith.opencrm.identity.Team;
import de.julith.opencrm.identity.TeamRepository;
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
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    public TenantProvisioningService(TenantRepository tenantRepository, TeamRepository teamRepository,
                                     org.springframework.jdbc.core.JdbcTemplate jdbcTemplate,
                                     org.springframework.context.ApplicationEventPublisher eventPublisher) {
        this.tenantRepository = tenantRepository;
        this.teamRepository = teamRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Tenant provision(String name, String slug) {
        String normalizedSlug = slug.trim().toLowerCase(Locale.ROOT);
        Tenant tenant = tenantRepository.save(new Tenant(UUID.randomUUID(), name.trim(), normalizedSlug));
        tenantRepository.flush();

        // Das Seeding muss die noch nicht committete tenants-Zeile sehen und die RLS-Policy
        // erfuellen: SET LOCAL auf der LAUFENDEN Transaktion (is_local=true), da das Token des
        // platform-admin keinen tenant_id-Claim traegt. Der Kontext endet mit dem Commit.
        jdbcTemplate.queryForObject("SELECT set_config('app.current_tenant', ?, true)", String.class,
                tenant.getId().toString());
        teamRepository.save(new Team(tenant.getId(), DEFAULT_TEAM_NAME));

        // Weitere Seeds (Default-Pipeline im sales-Modul) laufen entkoppelt nach Commit
        eventPublisher.publishEvent(new TenantProvisionedEvent(tenant.getId()));
        return tenant;
    }
}
