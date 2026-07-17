package de.julith.opencrm.activity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ActivityRepository extends JpaRepository<Activity, UUID> {

    Optional<Activity> findByIdAndDeletedAtIsNull(UUID id);

    @Query("""
            select a from Activity a
            where a.deletedAt is null
              and (:leadId is null or a.leadId = :leadId)
              and (:accountId is null or a.accountId = :accountId)
              and (:contactId is null or a.contactId = :contactId)
              and (:opportunityId is null or a.opportunityId = :opportunityId)
              and (:ownerId is null or a.ownerId = :ownerId)
              and (:openOnly = false or a.completedAt is null)
            order by a.createdAt desc, a.id desc
            """)
    List<Activity> findPage(@Param("leadId") UUID leadId, @Param("accountId") UUID accountId,
                            @Param("contactId") UUID contactId, @Param("opportunityId") UUID opportunityId,
                            @Param("ownerId") UUID ownerId, @Param("openOnly") boolean openOnly,
                            Pageable pageable);
}
