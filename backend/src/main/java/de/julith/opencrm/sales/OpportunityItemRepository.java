package de.julith.opencrm.sales;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OpportunityItemRepository extends JpaRepository<OpportunityItem, UUID> {

    List<OpportunityItem> findByOpportunityIdOrderByPosition(UUID opportunityId);

    Optional<OpportunityItem> findByIdAndOpportunityId(UUID id, UUID opportunityId);

    @org.springframework.data.jpa.repository.Query(
            "select coalesce(max(i.position), 0) from OpportunityItem i where i.opportunityId = :opportunityId")
    int maxPosition(@org.springframework.data.repository.query.Param("opportunityId") UUID opportunityId);
}
