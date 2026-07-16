package de.julith.opencrm.lead;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LeadRepository extends JpaRepository<Lead, UUID> {

    Optional<Lead> findByIdAndDeletedAtIsNull(UUID id);

    Optional<Lead> findByExternalIdAndDeletedAtIsNull(String externalId);

    Optional<Lead> findFirstByEmailIgnoreCaseAndDeletedAtIsNullOrderByCreatedAtAsc(String email);

    @Query("""
            select l from Lead l
            where l.deletedAt is null
              and (:status is null or l.status = :status)
              and (:ownerId is null or l.ownerId = :ownerId)
              and (:externalId is null or l.externalId = :externalId)
              and (:q is null or lower(l.companyName) like lower(concat('%', cast(:q as string), '%'))
                   or lower(l.lastName) like lower(concat('%', cast(:q as string), '%'))
                   or lower(l.email) like lower(concat('%', cast(:q as string), '%')))
            order by l.createdAt desc, l.id desc
            """)
    List<Lead> findPage(@Param("q") String q, @Param("status") Lead.Status status,
                        @Param("ownerId") UUID ownerId, @Param("externalId") String externalId, Pageable pageable);

    @Query("""
            select l from Lead l
            where l.deletedAt is null
              and (:status is null or l.status = :status)
              and (:ownerId is null or l.ownerId = :ownerId)
              and (:externalId is null or l.externalId = :externalId)
              and (:q is null or lower(l.companyName) like lower(concat('%', cast(:q as string), '%'))
                   or lower(l.lastName) like lower(concat('%', cast(:q as string), '%'))
                   or lower(l.email) like lower(concat('%', cast(:q as string), '%')))
              and (l.createdAt < :cursorCreatedAt or (l.createdAt = :cursorCreatedAt and l.id < :cursorId))
            order by l.createdAt desc, l.id desc
            """)
    List<Lead> findPageAfter(@Param("q") String q, @Param("status") Lead.Status status,
                             @Param("ownerId") UUID ownerId, @Param("externalId") String externalId,
                             @Param("cursorCreatedAt") OffsetDateTime cursorCreatedAt,
                             @Param("cursorId") UUID cursorId, Pageable pageable);
}
