package de.julith.opencrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.julith.opencrm.crmcore.GdprAnonymizationJob;
import de.julith.opencrm.lead.LeadSlaJob;
import de.julith.opencrm.reporting.MvRefreshJob;
import de.julith.opencrm.shared.tenancy.TenantScopedExecutor;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

/** M3: Dashboard mit Scopes, MV-Refresh, SLA-Benachrichtigung, DSGVO-Anonymisierung, Settings. */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ReportingFlowTest {

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
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri",
                () -> "http://localhost/unused-in-test");
    }

    private static final UUID TENANT = UUID.randomUUID();

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    TenantScopedExecutor tenantScopedExecutor;

    @Autowired
    MvRefreshJob mvRefreshJob;

    @Autowired
    LeadSlaJob leadSlaJob;

    @Autowired
    GdprAnonymizationJob gdprAnonymizationJob;

    private static JwtRequestPostProcessor adminJwt() {
        return jwt().jwt(jwt -> jwt.subject("kc-rep-admin").claim("tenant_id", TENANT.toString())
                        .claim("email", "admin@reporting.example").claim("name", "Report Admin"))
                .authorities(new SimpleGrantedAuthority("ROLE_tenant-admin"));
    }

    private static JwtRequestPostProcessor repJwt() {
        return jwt().jwt(jwt -> jwt.subject("kc-rep-user").claim("tenant_id", TENANT.toString())
                        .claim("email", "rep@reporting.example").claim("name", "Report Rep"))
                .authorities(new SimpleGrantedAuthority("ROLE_sales-rep"));
    }

    @Test
    @Order(1)
    void dashboardKpisFunnelAndTimeseries() throws Exception {
        seedTenant();
        // Datenbasis: Account, Produkt, gewonnene Opportunity ueber die API
        mockMvc.perform(get("/api/v1/leads").with(adminJwt())).andExpect(status().isOk());
        String accountJson = mockMvc.perform(post("/api/v1/accounts").with(adminJwt())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("name", "Reporting AG"))))
                .andReturn().getResponse().getContentAsString();
        UUID accountId = UUID.fromString(objectMapper.readTree(accountJson).get("id").asText());

        awaitDefaultPipeline();
        String oppJson = mockMvc.perform(post("/api/v1/opportunities").with(adminJwt())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "accountId", accountId, "name", "Reporting-Deal"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID oppId = UUID.fromString(objectMapper.readTree(oppJson).get("id").asText());
        mockMvc.perform(post("/api/v1/opportunities/" + oppId + "/estimate").with(adminJwt())
                        .contentType("application/json").content("{\"amount\": \"1200.00\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/opportunities/" + oppId + "/won").with(adminJwt()))
                .andExpect(status().isOk());

        String from = LocalDate.now().minusDays(7).toString();
        String to = LocalDate.now().plusDays(1).toString();
        mockMvc.perform(get("/api/v1/dashboard/kpis").with(adminJwt())
                        .param("from", from).param("to", to))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revenue").value(1200.00))
                .andExpect(jsonPath("$.wonCount").value(1))
                .andExpect(jsonPath("$.winRate").value(1.0));

        mockMvc.perform(get("/api/v1/dashboard/pipeline-funnel").with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Qualifizierung"));

        // MV-Refresh, dann Zeitreihe (heutiger Won kommt aus dem Live-Anteil)
        mvRefreshJob.refresh();
        String series = mockMvc.perform(get("/api/v1/dashboard/revenue-timeseries").with(adminJwt())
                        .param("from", from).param("to", to))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode seriesJson = objectMapper.readTree(series);
        assertThat(seriesJson).isNotEmpty();
        assertThat(seriesJson.get(seriesJson.size() - 1).get("revenue").decimalValue())
                .isEqualByComparingTo("1200.00");

        // Leaderboard: Admin ja, Rep 403
        mockMvc.perform(get("/api/v1/dashboard/leaderboard").with(adminJwt())
                        .param("from", from).param("to", to))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/dashboard/leaderboard").with(repJwt())
                        .param("from", from).param("to", to))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(2)
    void slaJobNotifiesOwnerOnce() throws Exception {
        mockMvc.perform(get("/api/v1/leads").with(repJwt())).andExpect(status().isOk());
        UUID repUserId = tenantScopedExecutor.callAs(TENANT, () ->
                jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = 'rep@reporting.example'",
                        UUID.class));

        String leadJson = mockMvc.perform(post("/api/v1/leads").with(adminJwt())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "SLA-Testlead", "companyName", "Langsam GmbH"))))
                .andReturn().getResponse().getContentAsString();
        UUID leadId = UUID.fromString(objectMapper.readTree(leadJson).get("id").asText());
        mockMvc.perform(post("/api/v1/leads/" + leadId + "/assign").with(adminJwt())
                        .contentType("application/json").content("{\"userId\":\"" + repUserId + "\"}"))
                .andExpect(status().isOk());

        // Zuweisung kuenstlich altern lassen (25 h), dann SLA-Job zweimal laufen lassen
        try (Connection connection = POSTGRES.createConnection("");
             PreparedStatement ps = connection.prepareStatement(
                     "UPDATE lead_assignments SET assigned_at = now() - interval '25 hours' WHERE lead_id = ?")) {
            ps.setObject(1, leadId);
            ps.execute();
        }
        tenantScopedExecutor.runAs(TENANT, leadSlaJob::checkCurrentTenant);
        tenantScopedExecutor.runAs(TENANT, leadSlaJob::checkCurrentTenant);

        String notifications = mockMvc.perform(get("/api/v1/notifications").with(repJwt()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode list = objectMapper.readTree(notifications);
        assertThat(list).hasSize(1);
        assertThat(list.get(0).get("type").asText()).isEqualTo("LEAD_SLA_BREACH");
        assertThat(list.get(0).get("payload").get("leadId").asText()).isEqualTo(leadId.toString());

        // Als gelesen markieren
        mockMvc.perform(post("/api/v1/notifications/" + list.get(0).get("id").asText() + "/read").with(repJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readAt").isNotEmpty());
    }

    @Test
    @Order(3)
    void gdprAnonymizationAndSettings() throws Exception {
        // Settings: SLA-Frist aendern, unbekannter Schluessel abgelehnt
        mockMvc.perform(patch("/api/v1/tenant-settings").with(adminJwt())
                        .contentType("application/json").content("{\"sla_hours\": 48}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settings.sla_hours").value(48));
        mockMvc.perform(patch("/api/v1/tenant-settings").with(adminJwt())
                        .contentType("application/json").content("{\"boese_einstellung\": 1}"))
                .andExpect(status().isBadRequest());

        // Disqualifizierter Lead, 13 Monate alt -> anonymisiert
        String leadJson = mockMvc.perform(post("/api/v1/leads").with(adminJwt())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Alter Lead", "companyName", "Vergessen GmbH",
                                "lastName", "Datenschutz", "email", "person@vergessen.example"))))
                .andReturn().getResponse().getContentAsString();
        UUID leadId = UUID.fromString(objectMapper.readTree(leadJson).get("id").asText());
        mockMvc.perform(post("/api/v1/leads/" + leadId + "/disqualify").with(adminJwt())
                        .contentType("application/json").content("{\"reason\":\"Kein Interesse\"}"))
                .andExpect(status().isOk());
        try (Connection connection = POSTGRES.createConnection("");
             PreparedStatement ps = connection.prepareStatement(
                     "UPDATE leads SET disqualified_at = now() - interval '13 months' WHERE id = ?")) {
            ps.setObject(1, leadId);
            ps.execute();
        }

        tenantScopedExecutor.runAs(TENANT, gdprAnonymizationJob::anonymizeCurrentTenant);

        mockMvc.perform(get("/api/v1/leads/" + leadId).with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").isEmpty())
                .andExpect(jsonPath("$.lastName").value("[anonymisiert]"));

        // DSGVO-Auskunft fuer einen Kontakt
        String contactJson = mockMvc.perform(post("/api/v1/contacts").with(adminJwt())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "lastName", "Auskunft", "email", "auskunft@example.com"))))
                .andReturn().getResponse().getContentAsString();
        UUID contactId = UUID.fromString(objectMapper.readTree(contactJson).get("id").asText());
        mockMvc.perform(get("/api/v1/contacts/" + contactId + "/gdpr-export").with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contact.email").value("auskunft@example.com"))
                .andExpect(jsonPath("$.exportedAt").isNotEmpty());
    }

    private void seedTenant() throws Exception {
        // Direkt als Superuser inkl. Provisionierungs-Seeds (Team + Default-Pipeline),
        // um nicht vom asynchronen Event-Listener abzuhaengen
        try (Connection connection = POSTGRES.createConnection("")) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO tenants (id, name, slug) VALUES (?, 'Reporting Tenant', ?) ON CONFLICT DO NOTHING")) {
                ps.setObject(1, TENANT);
                ps.setString(2, "reporting-" + TENANT.toString().substring(0, 8));
                ps.execute();
            }
            try (PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO pipelines (id, tenant_id, name, is_default)
                    VALUES (?, ?, 'Standard-Vertrieb', true) ON CONFLICT DO NOTHING""")) {
                UUID pipelineId = UUID.nameUUIDFromBytes(("pipeline-" + TENANT).getBytes());
                ps.setObject(1, pipelineId);
                ps.setObject(2, TENANT);
                ps.execute();
                try (PreparedStatement stages = connection.prepareStatement("""
                        INSERT INTO pipeline_stages (pipeline_id, name, sort_order, probability, is_won, is_lost)
                        VALUES (?, 'Qualifizierung', 10, 10, false, false),
                               (?, 'Gewonnen', 50, 100, true, false),
                               (?, 'Verloren', 60, 0, false, true)
                        ON CONFLICT DO NOTHING""")) {
                    stages.setObject(1, pipelineId);
                    stages.setObject(2, pipelineId);
                    stages.setObject(3, pipelineId);
                    stages.execute();
                }
            }
        }
    }

    private void awaitDefaultPipeline() throws Exception {
        mockMvc.perform(get("/api/v1/pipelines").with(adminJwt())).andExpect(status().isOk());
    }
}
