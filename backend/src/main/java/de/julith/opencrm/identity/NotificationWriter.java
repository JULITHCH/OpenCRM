package de.julith.opencrm.identity;

import de.julith.opencrm.shared.tenancy.TenantContext;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Öffentliche API des identity-Moduls zum Erzeugen von In-App-Benachrichtigungen. */
@Service
public class NotificationWriter {

    private final NotificationRepository notificationRepository;

    public NotificationWriter(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    public void notify(UUID userId, String type, Map<String, Object> payload) {
        notificationRepository.save(new Notification(TenantContext.get(), userId, type, payload));
    }

    public boolean alreadyNotifiedForLead(String type, UUID leadId) {
        return notificationRepository.existsByTypeAndLeadPayload(type, leadId.toString());
    }
}
