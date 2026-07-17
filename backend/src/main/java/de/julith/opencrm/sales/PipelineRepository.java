package de.julith.opencrm.sales;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PipelineRepository extends JpaRepository<Pipeline, UUID> {

    Optional<Pipeline> findByIsDefaultTrue();
}
