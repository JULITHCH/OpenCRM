package de.julith.opencrm.lead;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeadRepository extends JpaRepository<Lead, UUID> {
}
