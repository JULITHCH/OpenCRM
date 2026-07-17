package de.julith.opencrm.lead;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeadAssignmentRepository extends JpaRepository<LeadAssignment, UUID> {

    List<LeadAssignment> findByLeadIdOrderByAssignedAtDesc(UUID leadId);
}
