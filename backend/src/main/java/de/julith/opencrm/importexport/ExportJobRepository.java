package de.julith.opencrm.importexport;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExportJobRepository extends JpaRepository<ExportJob, UUID> {

    List<ExportJob> findByOrderByCreatedAtDesc(Pageable pageable);

    @Query("select count(e) from ExportJob e where e.status in ('PENDING', 'RUNNING')")
    long countActive();

    @Query("select count(e) from ExportJob e where e.createdAt > :since")
    long countCreatedSince(@Param("since") OffsetDateTime since);
}
