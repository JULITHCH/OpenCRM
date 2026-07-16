package de.julith.opencrm.lead;

import de.julith.opencrm.shared.tenancy.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/leads")
public class LeadController {

    private final LeadRepository leadRepository;

    public LeadController(LeadRepository leadRepository) {
        this.leadRepository = leadRepository;
    }

    public record LeadCreateRequest(@NotBlank String title, String companyName, String lastName, String email) {
    }

    public record LeadResponse(UUID id, String title, String companyName, String lastName, String email,
                               String status, UUID ownerId) {
        static LeadResponse from(Lead lead) {
            return new LeadResponse(lead.getId(), lead.getTitle(), lead.getCompanyName(), lead.getLastName(),
                    lead.getEmail(), lead.getStatus().name(), lead.getOwnerId());
        }
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<LeadResponse> list() {
        return leadRepository.findAll().stream().map(LeadResponse::from).toList();
    }

    @PostMapping
    @Transactional
    public ResponseEntity<LeadResponse> create(@Valid @RequestBody LeadCreateRequest request) {
        Lead lead = new Lead(TenantContext.get(), request.title(), request.companyName());
        lead.setLastName(request.lastName());
        lead.setEmail(request.email());
        Lead saved = leadRepository.save(lead);
        return ResponseEntity.created(URI.create("/api/v1/leads/" + saved.getId())).body(LeadResponse.from(saved));
    }
}
