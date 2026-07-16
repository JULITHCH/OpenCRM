package de.julith.opencrm.sales;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    Optional<Product> findByIdAndDeletedAtIsNull(UUID id);

    Optional<Product> findBySkuAndDeletedAtIsNull(String sku);

    Optional<Product> findByExternalIdAndDeletedAtIsNull(String externalId);

    @Query("""
            select p from Product p
            where p.deletedAt is null
              and (:activeOnly = false or p.active = true)
              and (:q is null or lower(p.name) like lower(concat('%', cast(:q as string), '%'))
                   or lower(p.sku) like lower(concat('%', cast(:q as string), '%')))
            order by p.createdAt desc, p.id desc
            """)
    List<Product> findPage(@Param("q") String q, @Param("activeOnly") boolean activeOnly, Pageable pageable);

    @Query("""
            select p from Product p
            where p.deletedAt is null
              and (:activeOnly = false or p.active = true)
              and (:q is null or lower(p.name) like lower(concat('%', cast(:q as string), '%'))
                   or lower(p.sku) like lower(concat('%', cast(:q as string), '%')))
              and (p.createdAt < :cursorCreatedAt or (p.createdAt = :cursorCreatedAt and p.id < :cursorId))
            order by p.createdAt desc, p.id desc
            """)
    List<Product> findPageAfter(@Param("q") String q, @Param("activeOnly") boolean activeOnly,
                                @Param("cursorCreatedAt") OffsetDateTime cursorCreatedAt,
                                @Param("cursorId") UUID cursorId, Pageable pageable);
}
