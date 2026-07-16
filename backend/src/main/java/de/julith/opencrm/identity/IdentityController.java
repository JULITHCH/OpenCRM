package de.julith.opencrm.identity;

import java.util.List;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Lese-Endpunkte für Nutzer und Teams (u. a. Namensauflösung und Zuweisungs-Auswahl im Frontend). */
@RestController
@RequestMapping("/api/v1")
public class IdentityController {

    private final UserRepository userRepository;
    private final TeamRepository teamRepository;

    public IdentityController(UserRepository userRepository, TeamRepository teamRepository) {
        this.userRepository = userRepository;
        this.teamRepository = teamRepository;
    }

    public record UserResponse(UUID id, String displayName, String email, String role, boolean active) {
        static UserResponse from(User user) {
            return new UserResponse(user.getId(), user.getDisplayName(), user.getEmail(), user.getRole(),
                    user.isActive());
        }
    }

    public record TeamResponse(UUID id, String name) {
        static TeamResponse from(Team team) {
            return new TeamResponse(team.getId(), team.getName());
        }
    }

    @GetMapping("/users")
    @Transactional(readOnly = true)
    public List<UserResponse> users(@RequestParam(required = false, defaultValue = "true") boolean activeOnly) {
        return userRepository.findAll().stream()
                .filter(user -> !activeOnly || user.isActive())
                .map(UserResponse::from)
                .toList();
    }

    @GetMapping("/teams")
    @Transactional(readOnly = true)
    public List<TeamResponse> teams() {
        return teamRepository.findAll().stream().map(TeamResponse::from).toList();
    }
}
