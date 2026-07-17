package de.julith.opencrm.importexport;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportJobRepository extends JpaRepository<ImportJob, UUID> {

    List<ImportJob> findByOrderByCreatedAtDesc(org.springframework.data.domain.Pageable pageable);
}
