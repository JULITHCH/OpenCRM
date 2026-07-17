package de.julith.opencrm.identity;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** In-App-Benachrichtigungen des angemeldeten Nutzers (Polling-Modell, E-30). */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public NotificationController(NotificationRepository notificationRepository, UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    public record NotificationResponse(UUID id, String type, Map<String, Object> payload, String readAt,
                                       String createdAt) {
        static NotificationResponse from(Notification n) {
            return new NotificationResponse(n.getId(), n.getType(), n.getPayload(),
                    n.getReadAt() != null ? n.getReadAt().toString() : null,
                    n.getCreatedAt() != null ? n.getCreatedAt().toString() : null);
        }
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<NotificationResponse> list(@RequestParam(required = false, defaultValue = "false") boolean unreadOnly,
                                           @RequestParam(required = false, defaultValue = "50") int limit,
                                           @AuthenticationPrincipal Jwt jwt) {
        return notificationRepository.findForUser(currentUserId(jwt), unreadOnly,
                        PageRequest.ofSize(Math.min(Math.max(limit, 1), 200)))
                .stream().map(NotificationResponse::from).toList();
    }

    @PostMapping("/{id}/read")
    @Transactional
    public NotificationResponse markRead(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Benachrichtigung " + id + " nicht gefunden"));
        if (!notification.getUserId().equals(currentUserId(jwt))) {
            throw new AccessDeniedException("Fremde Benachrichtigung");
        }
        notification.markRead();
        return NotificationResponse.from(notification);
    }

    @PostMapping("/read-all")
    @Transactional
    public Map<String, Integer> markAllRead(@AuthenticationPrincipal Jwt jwt) {
        List<Notification> unread = notificationRepository.findForUser(currentUserId(jwt), true,
                PageRequest.ofSize(500));
        unread.forEach(Notification::markRead);
        return Map.of("marked", unread.size());
    }

    private UUID currentUserId(Jwt jwt) {
        return userRepository.findByKeycloakId(jwt.getSubject()).map(User::getId)
                .orElseThrow(() -> new NoSuchElementException("Nutzer nicht provisioniert"));
    }
}
