package de.julith.opencrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
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
 * M2-Gesamtfluss über die HTTP-Schicht: Tenant-Provisionierung (inkl. asynchronem
 * Pipeline-Seeding), Team-Round-Robin per Regel, Produkte/Opportunities mit
 * Positionsberechnung und Won, Lead-Konvertierung.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SalesFlowTest {

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

    static UUID tenantId;
    static UUID managerUserId;
    static UUID repUserId;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    private static JwtRequestPostProcessor platformAdminJwt() {
        return jwt().jwt(jwt -> jwt.subject("kc-platform-admin").claim("email", "ops@julith.example")
                        .claim("name", "Platform Ops"))
                .authorities(new SimpleGrantedAuthority("ROLE_platform-admin"));
    }

    private static JwtRequestPostProcessor managerJwt() {
        return jwt().jwt(jwt -> jwt.subject("kc-sales-manager").claim("tenant_id", tenantId.toString())
                        .claim("email", "maria@sales.example").claim("name", "Maria Manager"))
                .authorities(new SimpleGrantedAuthority("ROLE_sales-manager"));
    }

    private static JwtRequestPostProcessor repJwt() {
        return jwt().jwt(jwt -> jwt.subject("kc-sales-rep").claim("tenant_id", tenantId.toString())
                        .claim("email", "jonas@sales.example").claim("name", "Jonas Rep"))
                .authorities(new SimpleGrantedAuthority("ROLE_sales-rep"));
    }

    @Test
    @Order(1)
    void provisioningSeedsTeamAndPipeline() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("name", "Sales Flow GmbH", "slug", "sales-flow"));
        String created = mockMvc.perform(post("/api/v1/tenants").with(platformAdminJwt())
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.plan").value("standard"))
                .andReturn().getResponse().getContentAsString();
        tenantId = UUID.fromString(objectMapper.readTree(created).get("id").asText());

        // Default-Team ist synchron da
        String teams = mockMvc.perform(get("/api/v1/teams").with(managerJwt()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(teams)).hasSize(1);

        // Default-Pipeline entsteht asynchron nach Commit (Event-Listener)
        JsonNode pipelines = awaitPipelines();
        assertThat(pipelines).hasSize(1);
        assertThat(pipelines.get(0).get("isDefault").asBoolean()).isTrue();
        assertThat(pipelines.get(0).get("stages")).hasSize(6);
    }

    @Test
    @Order(2)
    void ruleBasedRoundRobinAssignsWebFormLeads() throws Exception {
        // JIT-Provisionierung beider Nutzer, dann users.id einsammeln
        mockMvc.perform(get("/api/v1/leads").with(managerJwt())).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/leads").with(repJwt())).andExpect(status().isOk());
        JsonNode users = objectMapper.readTree(mockMvc.perform(get("/api/v1/users").with(managerJwt()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        for (JsonNode user : users) {
            if (user.get("email").asText().startsWith("maria")) {
                managerUserId = UUID.fromString(user.get("id").asText());
            }
            if (user.get("email").asText().startsWith("jonas")) {
                repUserId = UUID.fromString(user.get("id").asText());
            }
        }

        UUID teamId = UUID.fromString(objectMapper.readTree(
                mockMvc.perform(get("/api/v1/teams").with(managerJwt()))
                        .andReturn().getResponse().getContentAsString()).get(0).get("id").asText());
        mockMvc.perform(post("/api/v1/teams/" + teamId + "/members").with(managerJwt())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                Map.of("userId", managerUserId, "isLead", true, "isPrimary", true))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/teams/" + teamId + "/members").with(managerJwt())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                Map.of("userId", repUserId, "isPrimary", true))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/assignment-rules").with(managerJwt())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Webformular an Vertrieb", "priority", 10,
                                "criteria", Map.of("sources", java.util.List.of("WEB_FORM")),
                                "targetType", "TEAM", "targetId", teamId))))
                .andExpect(status().isCreated());

        // Zwei Web-Leads: Round-Robin verteilt auf verschiedene Mitglieder
        UUID owner1 = createLeadAndGetOwner("Web-Anfrage 1", "WEB_FORM");
        UUID owner2 = createLeadAndGetOwner("Web-Anfrage 2", "WEB_FORM");
        assertThat(owner1).isNotNull();
        assertThat(owner2).isNotNull();
        assertThat(owner1).isNotEqualTo(owner2);
    }

    @Test
    @Order(3)
    void opportunityAmountFromItemsAndWin() throws Exception {
        String accountJson = mockMvc.perform(post("/api/v1/accounts").with(managerJwt())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("name", "Flow Kunde AG"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID accountId = UUID.fromString(objectMapper.readTree(accountJson).get("id").asText());

        String productJson = mockMvc.perform(post("/api/v1/products").with(managerJwt())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "sku", "CRM-LIC", "name", "CRM-Lizenz", "listPrice", "99.50"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andReturn().getResponse().getContentAsString();
        UUID productId = UUID.fromString(objectMapper.readTree(productJson).get("id").asText());

        String oppJson = mockMvc.perform(post("/api/v1/opportunities").with(managerJwt())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "accountId", accountId, "name", "Lizenzdeal"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andReturn().getResponse().getContentAsString();
        UUID oppId = UUID.fromString(objectMapper.readTree(oppJson).get("id").asText());

        // Position: 10 x 99.50 mit 10 % Rabatt = 895.50 (Preis kommt aus list_price)
        mockMvc.perform(post("/api/v1/opportunities/" + oppId + "/items").with(managerJwt())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "productId", productId, "quantity", "10", "discountPct", "10"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.unitPrice").value(99.50))
                .andExpect(jsonPath("$.lineAmount").value(895.50));

        mockMvc.perform(get("/api/v1/opportunities/" + oppId).with(managerJwt()))
                .andExpect(jsonPath("$.amount").value(895.50))
                .andExpect(jsonPath("$.isEstimated").value(false));

        // Schaetzbetrag ist mit Positionen nicht mehr erlaubt (E-14)
        mockMvc.perform(post("/api/v1/opportunities/" + oppId + "/estimate").with(managerJwt())
                        .contentType("application/json").content("{\"amount\": \"5000\"}"))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/v1/opportunities/" + oppId + "/won").with(managerJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WON"));
    }

    @Test
    @Order(4)
    void leadConversionCreatesAccountContactOpportunity() throws Exception {
        String leadJson = mockMvc.perform(post("/api/v1/leads").with(managerJwt())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Grossprojekt", "companyName", "Konversion GmbH",
                                "lastName", "Kunde", "email", "kunde@konversion.example"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID leadId = UUID.fromString(objectMapper.readTree(leadJson).get("id").asText());

        mockMvc.perform(post("/api/v1/leads/" + leadId + "/contacted").with(managerJwt()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/leads/" + leadId + "/qualify").with(managerJwt()))
                .andExpect(status().isOk());

        String conversionJson = mockMvc.perform(post("/api/v1/leads/" + leadId + "/convert").with(managerJwt())
                        .contentType("application/json").content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lead.status").value("CONVERTED"))
                .andReturn().getResponse().getContentAsString();
        JsonNode conversion = objectMapper.readTree(conversionJson);
        assertThat(conversion.get("accountId").isNull()).isFalse();
        assertThat(conversion.get("contactId").isNull()).isFalse();
        assertThat(conversion.get("opportunityId").isNull()).isFalse();

        mockMvc.perform(get("/api/v1/opportunities/" + conversion.get("opportunityId").asText())
                        .with(managerJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.leadId").value(leadId.toString()));
    }

    private UUID createLeadAndGetOwner(String title, String source) throws Exception {
        String json = mockMvc.perform(post("/api/v1/leads").with(managerJwt())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", title, "companyName", title + " GmbH", "source", source))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andReturn().getResponse().getContentAsString();
        JsonNode owner = objectMapper.readTree(json).get("ownerId");
        return owner.isNull() ? null : UUID.fromString(owner.asText());
    }

    private JsonNode awaitPipelines() throws Exception {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(15));
        while (Instant.now().isBefore(deadline)) {
            String json = mockMvc.perform(get("/api/v1/pipelines").with(managerJwt()))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            JsonNode pipelines = objectMapper.readTree(json);
            if (!pipelines.isEmpty()) {
                return pipelines;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("Default-Pipeline wurde nicht rechtzeitig angelegt");
    }
}
