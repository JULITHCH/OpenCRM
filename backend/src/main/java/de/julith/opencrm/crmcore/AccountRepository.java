package de.julith.opencrm.crmcore;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    Optional<Account> findByIdAndDeletedAtIsNull(UUID id);

    @Query("""
            select a from Account a
            where a.deletedAt is null
              and (:q is null or lower(a.name) like lower(concat('%', cast(:q as string), '%')))
            order by a.createdAt desc, a.id desc
            """)
    List<Account> findPage(@Param("q") String q, Pageable pageable);

    @Query("""
            select a from Account a
            where a.deletedAt is null
              and (:q is null or lower(a.name) like lower(concat('%', cast(:q as string), '%')))
              and (a.createdAt < :cursorCreatedAt or (a.createdAt = :cursorCreatedAt and a.id < :cursorId))
            order by a.createdAt desc, a.id desc
            """)
    List<Account> findPageAfter(@Param("q") String q,
                                @Param("cursorCreatedAt") OffsetDateTime cursorCreatedAt,
                                @Param("cursorId") UUID cursorId,
                                Pageable pageable);
}
