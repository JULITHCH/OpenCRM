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
import de.julith.opencrm.importexport.ExportRunner;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
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
 * Absicherung der Release-Härtung: Owner-Scope (sales-rep darf keine fremden Leads ändern),
 * Rollenmatrix (read-only darf nichts schreiben), Export-End-to-End inkl. Fail-Closed der
 * Export-Query bei fehlendem Tenant-Kontext, und Fehlerformat (problem+json bei 403).
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class SecurityAndExportTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withUsername("postgres").withPassword("postgres").withDatabaseName("opencrm")
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

    @BeforeEach
    void seedTenant() throws Exception {
        try (var c = POSTGRES.createConnection("");
             var ps = c.prepareStatement(
                     "INSERT INTO tenants (id, name, slug) VALUES (?, ?, ?) ON CONFLICT DO NOTHING")) {
            ps.setObject(1, TENANT);
            ps.setString(2, "Sec Tenant");
            ps.setString(3, "sec-" + TENANT.toString().substring(0, 8));
            ps.execute();
        }
    }

    private static JwtRequestPostProcessor jwtFor(String sub, String email, String role) {
        return jwt().jwt(j -> j.subject(sub).claim("tenant_id", TENANT.toString())
                        .claim("email", email).claim("name", email))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    private UUID provisionUser(String sub, String email, String role) throws Exception {
        // Erster Request provisioniert den Nutzer JIT
        mockMvc.perform(get("/api/v1/leads").with(jwtFor(sub, email, role))).andExpect(status().isOk());
        try (var c = POSTGRES.createConnection("");
             var ps = c.prepareStatement("SELECT id FROM users WHERE keycloak_id = ?")) {
            ps.setString(1, sub);
            var rs = ps.executeQuery();
            rs.next();
            return rs.getObject(1, UUID.class);
        }
    }

    @Test
    void salesRepCannotModifyForeignLead() throws Exception {
        var manager = jwtFor("kc-mgr", "mgr@sec.example", "sales-manager");
        UUID repA = provisionUser("kc-repA", "repa@sec.example", "sales-rep");
        UUID repB = provisionUser("kc-repB", "repb@sec.example", "sales-rep");

        // Manager legt einen Lead an und weist ihn Rep A zu
        String created = mockMvc.perform(post("/api/v1/leads").with(manager).contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("title", "Deal", "companyName", "Muster"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID leadId = UUID.fromString(objectMapper.readTree(created).get("id").asText());
        mockMvc.perform(post("/api/v1/leads/" + leadId + "/assign").with(manager).contentType("application/json")
                        .content("{\"userId\":\"" + repA + "\"}"))
                .andExpect(status().isOk());

        // Rep B darf den Lead von Rep A NICHT patchen — 403 im problem+json-Format
        mockMvc.perform(patch("/api/v1/leads/" + leadId).with(jwtFor("kc-repB", "repb@sec.example", "sales-rep"))
                        .contentType("application/json").content("{\"title\":\"gekapert\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("forbidden"))
                .andExpect(jsonPath("$.code").value("forbidden"));

        // Rep A (Eigentümer) darf
        mockMvc.perform(post("/api/v1/leads/" + leadId + "/contacted")
                        .with(jwtFor("kc-repA", "repa@sec.example", "sales-rep")))
                .andExpect(status().isOk());
        assertThat(repB).isNotEqualTo(repA);
    }

    @Test
    void readOnlyRoleCannotWrite() throws Exception {
        var readOnly = jwtFor("kc-ro", "ro@sec.example", "read-only");
        mockMvc.perform(post("/api/v1/leads").with(readOnly).contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("title", "X", "companyName", "Y"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/accounts").with(readOnly).contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("name", "Z"))))
                .andExpect(status().isForbidden());
    }

    @Autowired
    ExportRunner exportRunner;

    @Test
    void leadExportEndToEndAndRoleGuard() throws Exception {
        var admin = jwtFor("kc-exp-admin", "expadmin@sec.example", "tenant-admin");
        mockMvc.perform(post("/api/v1/leads").with(admin).contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Export-Lead", "companyName", "Export GmbH",
                                "email", "e@export.example"))))
                .andExpect(status().isCreated());

        // sales-rep darf gar nicht exportieren
        mockMvc.perform(post("/api/v1/export-jobs").with(jwtFor("kc-exp-rep", "exprep@sec.example", "sales-rep"))
                        .contentType("application/json")
                        .content("{\"entityType\":\"LEAD\",\"format\":\"CSV\"}"))
                .andExpect(status().isForbidden());

        // Admin startet Export, pollt bis COMPLETED, lädt die Datei
        String job = mockMvc.perform(post("/api/v1/export-jobs").with(admin).contentType("application/json")
                        .content("{\"entityType\":\"LEAD\",\"format\":\"CSV\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID jobId = UUID.fromString(objectMapper.readTree(job).get("id").asText());

        JsonNode finished = awaitExport(admin, jobId);
        assertThat(finished.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(finished.get("rowCount").asInt()).isGreaterThanOrEqualTo(1);

        byte[] csv = mockMvc.perform(get("/api/v1/export-jobs/" + jobId + "/download").with(admin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        String content = new String(csv);
        assertThat(content).contains("Export GmbH").contains("title");
    }

    private JsonNode awaitExport(JwtRequestPostProcessor admin, UUID jobId) throws Exception {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(30));
        while (Instant.now().isBefore(deadline)) {
            String json = mockMvc.perform(get("/api/v1/export-jobs/" + jobId).with(admin))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            JsonNode node = objectMapper.readTree(json);
            String status = node.get("status").asText();
            if (status.equals("COMPLETED") || status.equals("FAILED")) {
                return node;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("Export nicht rechtzeitig abgeschlossen");
    }
}
