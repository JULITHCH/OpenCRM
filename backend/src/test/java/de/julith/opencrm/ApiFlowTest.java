package de.julith.opencrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.julith.opencrm.identity.User;
import de.julith.opencrm.identity.UserRepository;
import de.julith.opencrm.shared.tenancy.TenantScopedExecutor;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

/**
 * End-to-End über die HTTP-Schicht (MockMvc, echte Filterkette): JIT-Provisionierung,
 * Lead anlegen, manuell zuweisen (inkl. Historie), Statusübergänge, Rollenschutz.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class ApiFlowTest {

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
    private static final String MANAGER_SUBJECT = "kc-manager-" + TENANT;
    private static final String REP_SUBJECT = "kc-rep-" + TENANT;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UserRepository userRepository;

    @Autowired
    TenantScopedExecutor tenantScopedExecutor;

    @BeforeEach
    void seedTenant() throws Exception {
        try (Connection connection = POSTGRES.createConnection("");
             PreparedStatement ps = connection.prepareStatement(
                     "INSERT INTO tenants (id, name, slug) VALUES (?, ?, ?) ON CONFLICT DO NOTHING")) {
            ps.setObject(1, TENANT);
            ps.setString(2, "Flow Tenant");
            ps.setString(3, "flow-" + TENANT.toString().substring(0, 8));
            ps.execute();
        }
    }

    private static JwtRequestPostProcessor managerJwt() {
        return jwt()
                .jwt(jwt -> jwt.subject(MANAGER_SUBJECT)
                        .claim("tenant_id", TENANT.toString())
                        .claim("email", "maria@example.com")
                        .claim("name", "Maria Manager"))
                .authorities(new SimpleGrantedAuthority("ROLE_sales-manager"));
    }

    private static JwtRequestPostProcessor repJwt() {
        return jwt()
                .jwt(jwt -> jwt.subject(REP_SUBJECT)
                        .claim("tenant_id", TENANT.toString())
                        .claim("email", "jonas@example.com")
                        .claim("name", "Jonas Rep"))
                .authorities(new SimpleGrantedAuthority("ROLE_sales-rep"));
    }

    @Test
    void fullLeadFlowWithJitProvisioningAndAssignment() throws Exception {
        // 1) Erster Request des Reps: JIT legt die users-Zeile an
        mockMvc.perform(get("/api/v1/leads").with(repJwt()))
                .andExpect(status().isOk());
        UUID repUserId = tenantScopedExecutor.callAs(TENANT,
                () -> userRepository.findByKeycloakId(REP_SUBJECT).map(User::getId).orElseThrow());

        // 2) Manager legt einen Lead an
        String body = objectMapper.writeValueAsString(java.util.Map.of(
                "title", "Anfrage Website-Relaunch",
                "companyName", "Muster GmbH",
                "email", "kontakt@muster.example"));
        String created = mockMvc.perform(post("/api/v1/leads").with(managerJwt())
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("NEW"))
                .andReturn().getResponse().getContentAsString();
        UUID leadId = UUID.fromString(objectMapper.readTree(created).get("id").asText());

        // 3) Manager weist den Lead dem Rep zu: Status ASSIGNED plus Historieneintrag
        mockMvc.perform(post("/api/v1/leads/" + leadId + "/assign").with(managerJwt())
                        .contentType("application/json")
                        .content("{\"userId\":\"" + repUserId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.ownerId").value(repUserId.toString()));

        String assignments = mockMvc.perform(get("/api/v1/leads/" + leadId + "/assignments").with(managerJwt()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode history = objectMapper.readTree(assignments);
        assertThat(history).hasSize(1);
        assertThat(history.get(0).get("assignedTo").asText()).isEqualTo(repUserId.toString());
        assertThat(history.get(0).get("method").asText()).isEqualTo("MANUAL");

        // 4) Statusuebergaenge: contacted -> qualify; unerlaubter Sprung wird abgelehnt
        mockMvc.perform(post("/api/v1/leads/" + leadId + "/contacted").with(repJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONTACTED"));
        mockMvc.perform(post("/api/v1/leads/" + leadId + "/contacted").with(repJwt()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("invalid_state_transition"));
        mockMvc.perform(post("/api/v1/leads/" + leadId + "/qualify").with(repJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("QUALIFIED"));

        // 5) Disqualifikation ohne Grund ist abgelehnt, mit Grund ok
        mockMvc.perform(post("/api/v1/leads/" + leadId + "/disqualify").with(repJwt())
                        .contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/leads/" + leadId + "/disqualify").with(repJwt())
                        .contentType("application/json").content("{\"reason\":\"Kein Budget\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISQUALIFIED"));
    }

    @Test
    void salesRepMustNotAssignLeads() throws Exception {
        mockMvc.perform(post("/api/v1/leads/" + UUID.randomUUID() + "/assign").with(repJwt())
                        .contentType("application/json")
                        .content("{\"userId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void accountsAndContactsCrudWorks() throws Exception {
        String accountBody = objectMapper.writeValueAsString(java.util.Map.of(
                "name", "Alpha Handel AG", "city", "Hamburg", "postalCode", "20095"));
        String accountJson = mockMvc.perform(post("/api/v1/accounts").with(managerJwt())
                        .contentType("application/json").content(accountBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID accountId = UUID.fromString(objectMapper.readTree(accountJson).get("id").asText());

        mockMvc.perform(post("/api/v1/contacts").with(managerJwt())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "lastName", "Schulz", "firstName", "Petra",
                                "email", "p.schulz@alpha.example", "accountId", accountId.toString()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountId").value(accountId.toString()));

        mockMvc.perform(get("/api/v1/contacts").with(repJwt()).param("accountId", accountId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].lastName").value("Schulz"));
    }
}
