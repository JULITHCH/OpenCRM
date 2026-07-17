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
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    public IdentityController(UserRepository userRepository, TeamRepository teamRepository,
                              org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) {
        this.userRepository = userRepository;
        this.teamRepository = teamRepository;
        this.jdbcTemplate = jdbcTemplate;
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

    public record MemberRequest(@jakarta.validation.constraints.NotNull UUID userId, Boolean isLead,
                                Boolean isPrimary) {
    }

    public record MemberResponse(UUID userId, String displayName, boolean isLead, boolean isPrimary) {
    }

    @GetMapping("/teams/{teamId}/members")
    @Transactional(readOnly = true)
    public List<MemberResponse> members(@org.springframework.web.bind.annotation.PathVariable UUID teamId) {
        requireTeam(teamId);
        return jdbcTemplate.query("""
                SELECT tm.user_id, u.display_name, tm.is_lead, tm.is_primary
                FROM team_members tm JOIN users u ON u.id = tm.user_id
                WHERE tm.team_id = ? ORDER BY u.display_name
                """,
                (rs, rowNum) -> new MemberResponse(rs.getObject(1, UUID.class), rs.getString(2),
                        rs.getBoolean(3), rs.getBoolean(4)),
                teamId);
    }

    @org.springframework.web.bind.annotation.PostMapping("/teams/{teamId}/members")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('tenant-admin', 'sales-manager')")
    @Transactional
    public List<MemberResponse> addMember(@org.springframework.web.bind.annotation.PathVariable UUID teamId,
                                          @jakarta.validation.Valid
                                          @org.springframework.web.bind.annotation.RequestBody MemberRequest request) {
        requireTeam(teamId);
        userRepository.findById(request.userId())
                .orElseThrow(() -> new java.util.NoSuchElementException(
                        "Nutzer " + request.userId() + " nicht gefunden"));
        boolean isPrimary = Boolean.TRUE.equals(request.isPrimary());
        if (isPrimary) {
            // genau ein Primaerteam je Nutzer (E-19)
            jdbcTemplate.update("UPDATE team_members SET is_primary = false WHERE user_id = ?", request.userId());
        }
        jdbcTemplate.update("""
                INSERT INTO team_members (team_id, user_id, is_lead, is_primary) VALUES (?, ?, ?, ?)
                ON CONFLICT (team_id, user_id) DO UPDATE SET is_lead = excluded.is_lead,
                    is_primary = excluded.is_primary
                """, teamId, request.userId(), Boolean.TRUE.equals(request.isLead()), isPrimary);
        return members(teamId);
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/teams/{teamId}/members/{userId}")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('tenant-admin', 'sales-manager')")
    @Transactional
    public org.springframework.http.ResponseEntity<Void> removeMember(
            @org.springframework.web.bind.annotation.PathVariable UUID teamId,
            @org.springframework.web.bind.annotation.PathVariable UUID userId) {
        requireTeam(teamId);
        jdbcTemplate.update("DELETE FROM team_members WHERE team_id = ? AND user_id = ?", teamId, userId);
        return org.springframework.http.ResponseEntity.noContent().build();
    }

    private void requireTeam(UUID teamId) {
        teamRepository.findById(teamId)
                .orElseThrow(() -> new java.util.NoSuchElementException("Team " + teamId + " nicht gefunden"));
    }
}
