package com.homekept.identity;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Read-only identity lookups exposed to other domains.
 *
 * <p>Cross-domain rule: other domains (subscription, visit) need a user's email and first
 * name to address transactional emails. They call this service rather than the
 * {@link UserRepository} or the {@link User} entity directly — only a narrow
 * {@link UserContact} crosses the boundary.
 */
@Service
public class UserQueryService {

    private final UserRepository userRepository;

    public UserQueryService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Resolves the email + first name for a user id, for addressing transactional emails.
     *
     * @param userId the identity-domain user id
     * @return the contact, or empty if no such user
     */
    @Transactional(readOnly = true)
    public Optional<UserContact> findContactById(Long userId) {
        return userRepository.findById(userId)
                .map(u -> new UserContact(u.getEmail(), u.getFirstName()));
    }

    /**
     * Resolves a display summary (name, email, role, status) for a set of user ids.
     *
     * <p>Used by the technician admin console ({@code TechnicianAdminService}) to display
     * technician identity without that domain touching {@link UserRepository} or
     * {@link User} directly — only the narrow {@link UserSummary} crosses the boundary.
     * This is internal staff data (not customer PII); it is never returned for the
     * subscriber-facing admin lists, which intentionally omit names per the no-PII rule.
     *
     * @param userIds the identity-domain user ids to resolve
     * @return map of user id → summary, for ids that exist (missing ids are simply absent)
     */
    @Transactional(readOnly = true)
    public Map<Long, UserSummary> findSummariesByIds(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> new UserSummary(
                        u.getId(), u.getEmail(), u.getFirstName(), u.getLastName(),
                        u.getRole().name(), u.getStatus().name())));
    }

    /** The minimal contact info needed to address an email. */
    public record UserContact(String email, String firstName) {}

    /**
     * Resolves the full name + email for a user id, for the customer app's account/settings
     * page. Never includes the password hash or any other internal field.
     *
     * @param userId the identity-domain user id
     * @return the profile, or empty if no such user
     */
    @Transactional(readOnly = true)
    public Optional<UserProfile> findProfileById(Long userId) {
        return userRepository.findById(userId)
                .map(u -> new UserProfile(u.getFirstName(), u.getLastName(), u.getEmail()));
    }

    /** Full name + email for the customer app's account/settings page. */
    public record UserProfile(String firstName, String lastName, String email) {}

    /** Display summary for admin rosters: name, email, role, and account status. */
    public record UserSummary(Long id, String email, String firstName, String lastName,
                               String role, String status) {}
}
