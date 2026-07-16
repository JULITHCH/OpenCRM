package de.julith.opencrm.importexport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.julith.opencrm.lead.LeadRepository;
import de.julith.opencrm.shared.tenancy.TenantScopedExecutor;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class ImportFlowTest {

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
    LeadRepository leadRepository;

    @Autowired
    TenantScopedExecutor tenantScopedExecutor;

    @BeforeEach
    void seedTenant() throws Exception {
        try (Connection connection = POSTGRES.createConnection("");
             PreparedStatement ps = connection.prepareStatement(
                     "INSERT INTO tenants (id, name, slug) VALUES (?, ?, ?) ON CONFLICT DO NOTHING")) {
            ps.setObject(1, TENANT);
            ps.setString(2, "Import Tenant");
            ps.setString(3, "import-" + TENANT.toString().substring(0, 8));
            ps.execute();
        }
    }

    private static JwtRequestPostProcessor adminJwt() {
        return jwt()
                .jwt(jwt -> jwt.subject("kc-import-admin-" + TENANT)
                        .claim("tenant_id", TENANT.toString())
                        .claim("email", "admin@import.example")
                        .claim("name", "Import Admin"))
                .authorities(new SimpleGrantedAuthority("ROLE_tenant-admin"));
    }

    @Test
    void csvImportEndToEnd() throws Exception {
        String csv = """
                Firma,Nachname,E-Mail,Quelle,Punkte
                Muster GmbH,Meier,meier@muster.example,WEB_FORM,10
                ,,keine-mail,FALSCHE_QUELLE,xx
                Beta AG,Schmidt,schmidt@beta.example,,20
                """;
        MockMultipartFile file = new MockMultipartFile("file", "leads.csv", "text/csv",
                csv.getBytes(StandardCharsets.UTF_8));

        // 1) Upload: Job PENDING, Header erkannt
        String uploadJson = mockMvc.perform(multipart("/api/v1/import-jobs").file(file)
                        .param("entityType", "LEAD").with(adminJwt()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        UUID jobId = UUID.fromString(objectMapper.readTree(uploadJson).get("id").asText());

        // 2) Mapping-Vorschlag aus den deutschen Headern
        String suggestionJson = mockMvc.perform(get("/api/v1/import-jobs/" + jobId + "/mapping-suggestion")
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode suggestion = objectMapper.readTree(suggestionJson);
        assertThat(suggestion.get("Firma").asText()).isEqualTo("companyName");
        assertThat(suggestion.get("E-Mail").asText()).isEqualTo("email");

        String mappingBody = """
                {"mapping": {"Firma": "companyName", "Nachname": "lastName", "E-Mail": "email",
                             "Quelle": "source", "Punkte": "score"},
                 "options": {"duplicateStrategy": "SKIP"}}
                """;

        // 3) Dry-Run: 3 Zeilen, 1 fehlerhafte (4 Einzelfehler: Pflichtfeld, E-Mail, Enum, Zahl),
        //    keine Leads geschrieben
        mockMvc.perform(post("/api/v1/import-jobs/" + jobId + "/validate").with(adminJwt())
                        .contentType("application/json").content(mappingBody))
                .andExpect(status().isOk());
        JsonNode dryRun = awaitFinished(jobId);
        assertThat(dryRun.get("status").asText()).isEqualTo("COMPLETED_WITH_ERRORS");
        assertThat(dryRun.get("totalRows").asInt()).isEqualTo(3);
        assertThat(dryRun.get("errorRows").asInt()).isEqualTo(1);
        long leadsAfterDryRun = tenantScopedExecutor.callAs(TENANT, leadRepository::count);
        assertThat(leadsAfterDryRun).isZero();

        String errorsJson = mockMvc.perform(get("/api/v1/import-jobs/" + jobId + "/errors").with(adminJwt()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode errors = objectMapper.readTree(errorsJson);
        assertThat(errors.size()).isEqualTo(4);

        // 4) Execute: 2 gueltige Leads entstehen
        mockMvc.perform(post("/api/v1/import-jobs/" + jobId + "/execute").with(adminJwt())
                        .contentType("application/json").content(mappingBody))
                .andExpect(status().isOk());
        JsonNode executed = awaitFinished(jobId);
        assertThat(executed.get("status").asText()).isEqualTo("COMPLETED_WITH_ERRORS");
        long leadsAfterExecute = tenantScopedExecutor.callAs(TENANT, leadRepository::count);
        assertThat(leadsAfterExecute).isEqualTo(2);

        // 5) Zweiter Execute mit SKIP: Duplikate (per E-Mail) werden uebersprungen
        mockMvc.perform(post("/api/v1/import-jobs/" + jobId + "/execute").with(adminJwt())
                        .contentType("application/json").content(mappingBody))
                .andExpect(status().isOk());
        JsonNode rerun = awaitFinished(jobId);
        assertThat(rerun.get("options").get("skippedRows").asInt()).isEqualTo(2);
        long leadsAfterRerun = tenantScopedExecutor.callAs(TENANT, leadRepository::count);
        assertThat(leadsAfterRerun).isEqualTo(2);
    }

    private JsonNode awaitFinished(UUID jobId) throws Exception {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(30));
        while (Instant.now().isBefore(deadline)) {
            String json = mockMvc.perform(get("/api/v1/import-jobs/" + jobId).with(adminJwt()))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            JsonNode job = objectMapper.readTree(json);
            String status = job.get("status").asText();
            if (status.startsWith("COMPLETED") || status.equals("FAILED")) {
                return job;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("Import-Job nicht rechtzeitig abgeschlossen");
    }
}
