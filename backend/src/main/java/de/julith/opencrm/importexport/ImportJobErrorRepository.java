package de.julith.opencrm.importexport;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportJobErrorRepository extends JpaRepository<ImportJobError, UUID> {

    List<ImportJobError> findByImportJobIdOrderByRowNumber(UUID importJobId, Pageable pageable);

    long countByImportJobId(UUID importJobId);

    void deleteByImportJobId(UUID importJobId);
}
