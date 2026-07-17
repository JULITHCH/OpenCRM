package de.julith.opencrm.importexport;

import de.julith.opencrm.lead.Lead;
import de.julith.opencrm.lead.LeadRepository;
import de.julith.opencrm.shared.tenancy.TenantContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class LeadRowImporter implements EntityRowImporter {

    private final LeadRepository leadRepository;

    public LeadRowImporter(LeadRepository leadRepository) {
        this.leadRepository = leadRepository;
    }

    @Override
    public ImportJob.EntityType entityType() {
        return ImportJob.EntityType.LEAD;
    }

    @Override
    public List<String> targetFields() {
        return List.of("title", "companyName", "firstName", "lastName", "email", "phone",
                "source", "score", "externalId");
    }

    @Override
    public List<RowError> validate(Map<String, String> row) {
        List<RowError> errors = new ArrayList<>();
        if (ImportSupport.trimToNull(row.get("companyName")) == null
                && ImportSupport.trimToNull(row.get("lastName")) == null) {
            errors.add(new RowError(null, "VALUE_REQUIRED",
                    "Mindestens eines von companyName und lastName ist Pflicht"));
        }
        ImportSupport.checkEmail(row, errors);
        ImportSupport.parseIntOrError(row, "score", errors);
        String source = ImportSupport.trimToNull(row.get("source"));
        if (source != null) {
            try {
                Lead.Source.valueOf(source.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                errors.add(new RowError("source", "INVALID_ENUM", "Unbekannte Quelle: " + source));
            }
        }
        return errors;
    }

    @Override
    public RowResult upsert(Map<String, String> row, DuplicateStrategy strategy, UUID defaultOwnerId) {
        Optional<Lead> existing = findDuplicate(row);
        if (existing.isPresent() && strategy == DuplicateStrategy.SKIP) {
            return RowResult.SKIPPED;
        }
        if (existing.isPresent() && strategy == DuplicateStrategy.UPDATE) {
            apply(existing.get(), row);
            return RowResult.UPDATED;
        }

        Lead lead = new Lead(TenantContext.get(),
                ImportSupport.trimToNull(row.get("title")),
                ImportSupport.trimToNull(row.get("companyName")));
        apply(lead, row);
        lead.setSource(resolveSource(row));
        String externalId = ImportSupport.trimToNull(row.get("externalId"));
        if (externalId != null) {
            // Entscheidung: Bei CREATE ("immer neu anlegen") mit bereits vergebener externalId legen
            // wir den neuen Lead OHNE externalId an. Sonst wuerde der partielle Unique-Index
            // uq_leads_tenant_external_id (tenant_id, external_id) WHERE external_id IS NOT NULL eine
            // DataIntegrityViolation werfen, die den kompletten Chunk killt. Bei SKIP/UPDATE ist an
            // dieser Stelle garantiert kein Duplikat vorhanden (findDuplicate war leer), daher keine
            // zusaetzliche Pruefung noetig.
            boolean externalIdTaken = strategy == DuplicateStrategy.CREATE
                    && leadRepository.findByExternalIdAndDeletedAtIsNull(externalId).isPresent();
            if (!externalIdTaken) {
                lead.setExternalId(externalId);
            }
        }
        if (defaultOwnerId != null) {
            lead.assignTo(defaultOwnerId);
        }
        leadRepository.save(lead);
        return RowResult.CREATED;
    }

    /** Match-Reihenfolge laut E-11: external_id vor E-Mail. */
    private Optional<Lead> findDuplicate(Map<String, String> row) {
        String externalId = ImportSupport.trimToNull(row.get("externalId"));
        if (externalId != null) {
            Optional<Lead> byExternalId = leadRepository.findByExternalIdAndDeletedAtIsNull(externalId);
            if (byExternalId.isPresent()) {
                return byExternalId;
            }
        }
        String email = ImportSupport.trimToNull(row.get("email"));
        if (email != null) {
            return leadRepository.findFirstByEmailIgnoreCaseAndDeletedAtIsNullOrderByCreatedAtAsc(email);
        }
        return Optional.empty();
    }

    private static void apply(Lead lead, Map<String, String> row) {
        setIfPresent(row, "title", lead::setTitle);
        setIfPresent(row, "companyName", lead::setCompanyName);
        setIfPresent(row, "firstName", lead::setFirstName);
        setIfPresent(row, "lastName", lead::setLastName);
        setIfPresent(row, "email", lead::setEmail);
        setIfPresent(row, "phone", lead::setPhone);
        String score = ImportSupport.trimToNull(row.get("score"));
        if (score != null) {
            lead.setScore(Integer.valueOf(score));
        }
    }

    private static Lead.Source resolveSource(Map<String, String> row) {
        String source = ImportSupport.trimToNull(row.get("source"));
        return source == null ? Lead.Source.IMPORT : Lead.Source.valueOf(source.toUpperCase(Locale.ROOT));
    }

    private static void setIfPresent(Map<String, String> row, String field, java.util.function.Consumer<String> setter) {
        String value = ImportSupport.trimToNull(row.get(field));
        if (value != null) {
            setter.accept(value);
        }
    }
}
