package de.julith.opencrm.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.julith.opencrm.OpenCrmApplication;
import de.julith.opencrm.lead.Lead;
import de.julith.opencrm.lead.LeadRepository;
import de.julith.opencrm.shared.tenancy.TenantContext;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

/**
 * Beweist die Mandanten-Isolation aus docs/04-multi-tenancy.md gegen ein echtes PostgreSQL:
 * die Anwendung verbindet sich als opencrm_app (ohne BYPASSRLS), Flyway als opencrm_migrator.
 */
@Testcontainers
@SpringBootTest(properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost/unused-in-test"
})
class RlsIsolationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withUsername("postgres")
            .withPassword("postgres")
            .withDatabaseName("opencrm")
            .withCopyFileToContainer(
                    MountableFile.forHostPath("../infra/postgres/init/01-roles.sql"),
                    "/docker-entrypoint-initdb.d/01-roles.sql");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "opencrm_app");
        registry.add("spring.datasource.password", () -> "opencrm_app");
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", () -> "opencrm_migrator");
        registry.add("spring.flyway.password", () -> "opencrm_migrator");
    }

    private static final UUID TENANT_A = UUID.randomUUID();
    private static final UUID TENANT_B = UUID.randomUUID();

    @Autowired
    LeadRepository leadRepository;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Autowired
    DataSource dataSource;

    @BeforeEach
    void seedTenants() throws Exception {
        // Nach dem Spring-Start (Flyway ist gelaufen); Tenants direkt als Superuser anlegen —
        // die Provisionierung selbst ist nicht Gegenstand dieses Tests. Idempotent via ON CONFLICT.
        try (Connection connection = POSTGRES.createConnection("");
             PreparedStatement ps = connection.prepareStatement(
                     "INSERT INTO tenants (id, name, slug) VALUES (?, ?, ?) ON CONFLICT DO NOTHING")) {
            ps.setObject(1, TENANT_A);
            ps.setString(2, "Tenant A");
            ps.setString(3, "tenant-a");
            ps.execute();
            ps.setObject(1, TENANT_B);
            ps.setString(2, "Tenant B");
            ps.setString(3, "tenant-b");
            ps.execute();
        }
        // Leads zwischen den Tests aufraeumen, damit die Zaehl-Assertions unabhaengig bleiben
        try (Connection connection = POSTGRES.createConnection("");
             PreparedStatement ps = connection.prepareStatement("DELETE FROM leads")) {
            ps.execute();
        }
    }

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @Test
    void tenantsSeeOnlyTheirOwnRows() {
        createLead(TENANT_A, "Anfrage Messe", "Alpha GmbH");
        createLead(TENANT_A, "Webformular", "Anton AG");
        createLead(TENANT_B, "Empfehlung", "Beta KG");

        TenantContext.set(TENANT_A);
        var tenantALeads = transactionTemplate.execute(tx -> leadRepository.findAll());
        assertThat(tenantALeads).hasSize(2)
                .allSatisfy(lead -> assertThat(lead.getTenantId()).isEqualTo(TENANT_A));

        TenantContext.set(TENANT_B);
        var tenantBLeads = transactionTemplate.execute(tx -> leadRepository.findAll());
        assertThat(tenantBLeads).hasSize(1)
                .allSatisfy(lead -> assertThat(lead.getTenantId()).isEqualTo(TENANT_B));
    }

    @Test
    void withoutTenantContextNoRowsAreVisible() {
        createLead(TENANT_A, "Nur fuer A sichtbar", "Alpha GmbH");

        TenantContext.clear();
        var leads = transactionTemplate.execute(tx -> leadRepository.findAll());
        assertThat(leads).isEmpty();
    }

    @Test
    void insertForForeignTenantIsRejectedByPolicy() {
        TenantContext.set(TENANT_A);
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(tx ->
                leadRepository.saveAndFlush(new Lead(TENANT_B, "Fremder Tenant", "Beta KG"))))
                .hasMessageContaining("row-level security");
    }

    @Test
    void appRoleCannotBypassRls() throws Exception {
        createLead(TENANT_A, "Nicht global sichtbar", "Alpha GmbH");

        // Direkter JDBC-Zugriff als opencrm_app ohne Tenant-Kontext: 0 Zeilen (fail-closed)
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement("SELECT count(*) FROM leads");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            assertThat(rs.getLong(1)).isZero();
        }
    }

    private void createLead(UUID tenantId, String title, String company) {
        TenantContext.set(tenantId);
        transactionTemplate.executeWithoutResult(tx -> leadRepository.save(new Lead(tenantId, title, company)));
        TenantContext.clear();
    }
}
