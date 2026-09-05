package com.homekept.identity;

import com.homekept.identity.exception.InvalidAccountUpdateRequestException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The identity domain's single mutation entry point for self-serve profile edits
 * ({@code PATCH /api/app/account}, first name / last name / phone).
 *
 * <p>Other domains (the subscription domain's {@code SubscriptionAppService} owns the
 * account settings endpoint) must call this service rather than reach {@link UserRepository}
 * or {@link User} directly — mirrors how {@link UserQueryService} is the read-only crossing
 * point and {@link AuthService#createUser} is the crossing point for account creation.
 *
 * <p>Email and the service property address are deliberately not editable here: an email
 * change is an account-takeover-risk operation that needs dual verification (separate,
 * later work), and the property address drives routing and the property record (its own
 * admin-only update path, {@code PropertyService}).
 */
@Service
public class UserProfileService {

    /** Matches {@code User.firstName}/{@code User.lastName} column length. */
    static final int MAX_NAME_LENGTH = 100;

    /** Matches {@code User.phone} column length. */
    static final int MAX_PHONE_LENGTH = 30;

    private final UserRepository userRepository;

    public UserProfileService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Updates the authenticated user's first name, last name, and/or phone. Each parameter
     * is optional: {@code null} leaves the corresponding column unchanged. A non-null name
     * must be non-blank and at most {@value #MAX_NAME_LENGTH} characters; a non-null phone
     * must be at most {@value #MAX_PHONE_LENGTH} characters (phone may be blank — that is
     * how a customer clears a previously-captured number).
     *
     * @param userId    the authenticated user's id (JWT principal)
     * @param firstName new first name, or {@code null} to leave unchanged
     * @param lastName  new last name, or {@code null} to leave unchanged
     * @param phone     new phone, or {@code null} to leave unchanged
     * @return the updated profile (first name, last name, email)
     * @throws InvalidAccountUpdateRequestException if a provided field fails validation (400)
     */
    @Transactional
    public UserQueryService.UserProfile updateProfile(Long userId, String firstName, String lastName, String phone) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + userId));

        if (firstName != null) {
            requireNonBlankAndAtMost(firstName, MAX_NAME_LENGTH, "First name");
            user.setFirstName(firstName);
        }
        if (lastName != null) {
            requireNonBlankAndAtMost(lastName, MAX_NAME_LENGTH, "Last name");
            user.setLastName(lastName);
        }
        if (phone != null) {
            if (phone.length() > MAX_PHONE_LENGTH) {
                throw new InvalidAccountUpdateRequestException(
                        "Phone must be at most " + MAX_PHONE_LENGTH + " characters");
            }
            user.setPhone(phone);
        }

        userRepository.save(user);
        return new UserQueryService.UserProfile(
                user.getFirstName(), user.getLastName(), user.getEmail(), user.getPhone());
    }

    private void requireNonBlankAndAtMost(String value, int maxLength, String label) {
        if (value.isBlank()) {
            throw new InvalidAccountUpdateRequestException(label + " must not be blank");
        }
        if (value.length() > maxLength) {
            throw new InvalidAccountUpdateRequestException(
                    label + " must be at most " + maxLength + " characters");
        }
    }
}
