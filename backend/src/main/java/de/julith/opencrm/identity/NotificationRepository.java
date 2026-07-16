package de.julith.opencrm.identity;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    @Query("""
            select n from Notification n
            where n.userId = :userId and (:unreadOnly = false or n.readAt is null)
            order by n.createdAt desc
            """)
    List<Notification> findForUser(@Param("userId") UUID userId, @Param("unreadOnly") boolean unreadOnly,
                                   Pageable pageable);

    @Query(value = "SELECT EXISTS (SELECT 1 FROM notifications WHERE type = :type AND payload ->> 'leadId' = :leadId)",
            nativeQuery = true)
    boolean existsByTypeAndLeadPayload(@Param("type") String type, @Param("leadId") String leadId);
}
