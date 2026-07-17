package de.julith.opencrm.lead;

import de.julith.opencrm.crmcore.Account;
import de.julith.opencrm.crmcore.AccountRepository;
import de.julith.opencrm.crmcore.Contact;
import de.julith.opencrm.crmcore.ContactRepository;
import de.julith.opencrm.sales.Opportunity;
import de.julith.opencrm.sales.OpportunityService;
import de.julith.opencrm.shared.tenancy.TenantContext;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Konvertierung Lead -> Account + Contact + Opportunity in einer Transaktion
 * (docs/06 Abschnitt 6): bestehender Account kann uebergeben werden, sonst
 * entsteht er aus den Lead-Firmendaten; der Kontakt entsteht aus den Personendaten.
 */
@Service
public class LeadConversionService {

    public record ConversionResult(Lead lead, UUID accountId, UUID contactId, UUID opportunityId) {
    }

    private final LeadRepository leadRepository;
    private final AccountRepository accountRepository;
    private final ContactRepository contactRepository;
    private final OpportunityService opportunityService;

    public LeadConversionService(LeadRepository leadRepository, AccountRepository accountRepository,
                                 ContactRepository contactRepository, OpportunityService opportunityService) {
        this.leadRepository = leadRepository;
        this.accountRepository = accountRepository;
        this.contactRepository = contactRepository;
        this.opportunityService = opportunityService;
    }

    @Transactional
    public ConversionResult convert(UUID leadId, UUID existingAccountId, String opportunityName) {
        Lead lead = leadRepository.findByIdAndDeletedAtIsNull(leadId)
                .orElseThrow(() -> new NoSuchElementException("Lead " + leadId + " nicht gefunden"));
        if (lead.getStatus() != Lead.Status.QUALIFIED) {
            throw new IllegalStateException("Nur qualifizierte Leads koennen konvertiert werden (Status: "
                    + lead.getStatus() + ")");
        }

        Account account = resolveAccount(lead, existingAccountId);

        Contact contact = null;
        if (lead.getLastName() != null && !lead.getLastName().isBlank()) {
            contact = new Contact(TenantContext.get(), lead.getLastName());
            contact.setFirstName(lead.getFirstName());
            contact.setEmail(lead.getEmail());
            contact.setPhone(lead.getPhone());
            contact.setAccountId(account.getId());
            contact = contactRepository.save(contact);
        }

        String name = opportunityName != null && !opportunityName.isBlank()
                ? opportunityName
                : lead.getTitle() != null ? lead.getTitle() : "Opportunity " + account.getName();
        Opportunity opportunity = opportunityService.create(account.getId(), name, null,
                lead.getOwnerId(), null, lead.getId());

        lead.markConverted(account.getId(), contact != null ? contact.getId() : null, opportunity.getId());
        return new ConversionResult(lead, account.getId(),
                contact != null ? contact.getId() : null, opportunity.getId());
    }

    private Account resolveAccount(Lead lead, UUID existingAccountId) {
        if (existingAccountId != null) {
            return accountRepository.findByIdAndDeletedAtIsNull(existingAccountId)
                    .orElseThrow(() -> new NoSuchElementException(
                            "Account " + existingAccountId + " nicht gefunden"));
        }
        if (lead.getCompanyName() == null || lead.getCompanyName().isBlank()) {
            throw new IllegalArgumentException(
                    "Ohne companyName am Lead muss ein bestehender accountId uebergeben werden");
        }
        Account account = new Account(TenantContext.get(), lead.getCompanyName());
        account.setOwnerId(lead.getOwnerId());
        return accountRepository.save(account);
    }
}
