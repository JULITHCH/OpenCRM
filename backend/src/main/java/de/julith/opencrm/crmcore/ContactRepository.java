package de.julith.opencrm.crmcore;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContactRepository extends JpaRepository<Contact, UUID> {

    Optional<Contact> findByIdAndDeletedAtIsNull(UUID id);

    @Query("""
            select c from Contact c
            where c.deletedAt is null
              and (:accountId is null or c.accountId = :accountId)
              and (:q is null or lower(c.lastName) like lower(concat('%', cast(:q as string), '%'))
                   or lower(c.email) like lower(concat('%', cast(:q as string), '%')))
            order by c.createdAt desc, c.id desc
            """)
    List<Contact> findPage(@Param("q") String q, @Param("accountId") UUID accountId, Pageable pageable);

    @Query("""
            select c from Contact c
            where c.deletedAt is null
              and (:accountId is null or c.accountId = :accountId)
              and (:q is null or lower(c.lastName) like lower(concat('%', cast(:q as string), '%'))
                   or lower(c.email) like lower(concat('%', cast(:q as string), '%')))
              and (c.createdAt < :cursorCreatedAt or (c.createdAt = :cursorCreatedAt and c.id < :cursorId))
            order by c.createdAt desc, c.id desc
            """)
    List<Contact> findPageAfter(@Param("q") String q, @Param("accountId") UUID accountId,
                                @Param("cursorCreatedAt") OffsetDateTime cursorCreatedAt,
                                @Param("cursorId") UUID cursorId,
                                Pageable pageable);
}
