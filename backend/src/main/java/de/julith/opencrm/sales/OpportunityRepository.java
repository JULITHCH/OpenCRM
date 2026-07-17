package de.julith.opencrm.sales;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OpportunityRepository extends JpaRepository<Opportunity, UUID> {

    Optional<Opportunity> findByIdAndDeletedAtIsNull(UUID id);

    @Query("""
            select o from Opportunity o
            where o.deletedAt is null
              and (:status is null or o.status = :status)
              and (:accountId is null or o.accountId = :accountId)
              and (:ownerId is null or o.ownerId = :ownerId)
              and (:q is null or lower(o.name) like lower(concat('%', cast(:q as string), '%')))
            order by o.createdAt desc, o.id desc
            """)
    List<Opportunity> findPage(@Param("q") String q, @Param("status") Opportunity.Status status,
                               @Param("accountId") UUID accountId, @Param("ownerId") UUID ownerId,
                               Pageable pageable);

    @Query("""
            select o from Opportunity o
            where o.deletedAt is null
              and (:status is null or o.status = :status)
              and (:accountId is null or o.accountId = :accountId)
              and (:ownerId is null or o.ownerId = :ownerId)
              and (:q is null or lower(o.name) like lower(concat('%', cast(:q as string), '%')))
              and (o.createdAt < :cursorCreatedAt or (o.createdAt = :cursorCreatedAt and o.id < :cursorId))
            order by o.createdAt desc, o.id desc
            """)
    List<Opportunity> findPageAfter(@Param("q") String q, @Param("status") Opportunity.Status status,
                                    @Param("accountId") UUID accountId, @Param("ownerId") UUID ownerId,
                                    @Param("cursorCreatedAt") OffsetDateTime cursorCreatedAt,
                                    @Param("cursorId") UUID cursorId, Pageable pageable);
}
