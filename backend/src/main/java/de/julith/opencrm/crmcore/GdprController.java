package de.julith.opencrm.crmcore;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** DSGVO-Datenauskunft (Art. 15) je Kontakt als JSON-Export (docs/08 Abschnitt DSGVO). */
@RestController
@RequestMapping("/api/v1/contacts/{id}/gdpr-export")
@PreAuthorize("hasRole('tenant-admin')")
public class GdprController {

    private final ContactRepository contactRepository;
    private final JdbcTemplate jdbcTemplate;

    public GdprController(ContactRepository contactRepository, JdbcTemplate jdbcTemplate) {
        this.contactRepository = contactRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public Map<String, Object> export(@PathVariable UUID id) {
        Contact contact = contactRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new NoSuchElementException("Contact " + id + " nicht gefunden"));

        Map<String, Object> person = new java.util.LinkedHashMap<>();
        person.put("id", contact.getId().toString());
        person.put("firstName", contact.getFirstName());
        person.put("lastName", contact.getLastName());
        person.put("email", contact.getEmail());
        person.put("phone", contact.getPhone());
        person.put("position", contact.getPosition());
        person.put("gdprConsentAt",
                contact.getGdprConsentAt() != null ? contact.getGdprConsentAt().toString() : null);
        person.put("createdAt", contact.getCreatedAt() != null ? contact.getCreatedAt().toString() : null);

        String accountName = contact.getAccountId() == null ? null : jdbcTemplate.query(
                "SELECT name FROM accounts WHERE id = ?",
                rs -> rs.next() ? rs.getString(1) : null, contact.getAccountId());

        List<Map<String, Object>> activities = jdbcTemplate.queryForList("""
                SELECT type, subject, body, created_at FROM activities
                WHERE contact_id = ? AND deleted_at IS NULL ORDER BY created_at
                """, contact.getId()).stream()
                .map(row -> Map.<String, Object>of(
                        "type", row.get("type"),
                        "subject", row.get("subject"),
                        "body", row.get("body") != null ? row.get("body") : "",
                        "createdAt", row.get("created_at").toString()))
                .toList();

        // Leads derselben Person (Match ueber E-Mail); RLS grenzt automatisch auf den Tenant ein.
        List<Map<String, Object>> leads = contact.getEmail() == null ? List.of()
                : jdbcTemplate.queryForList("""
                        SELECT title, company_name, status, created_at FROM leads
                        WHERE lower(email) = lower(?) AND deleted_at IS NULL
                        ORDER BY created_at
                        """, contact.getEmail()).stream()
                        .map(row -> Map.<String, Object>of(
                                "title", row.get("title") != null ? row.get("title") : "",
                                "companyName", row.get("company_name") != null ? row.get("company_name") : "",
                                "status", row.get("status"),
                                "createdAt", row.get("created_at").toString()))
                        .toList();

        return Map.of(
                "exportedAt", java.time.OffsetDateTime.now().toString(),
                "contact", person,
                "account", accountName != null ? Map.of("name", accountName) : Map.of(),
                "activities", activities,
                "leads", leads);
    }
}
