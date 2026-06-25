package com.homekept.identity;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

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

    /** The minimal contact info needed to address an email. */
    public record UserContact(String email, String firstName) {}
}
