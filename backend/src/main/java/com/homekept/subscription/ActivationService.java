package com.homekept.subscription;

import com.homekept.booking.BookingService;
import com.homekept.booking.BookingService.BookingActivationData;
import com.homekept.identity.AuthService;
import com.homekept.identity.AuthService.TokenPair;
import com.homekept.identity.Role;
import com.homekept.identity.UserStatus;
import com.homekept.property.PropertyService;
import com.homekept.property.PropertyType;
import com.homekept.subscription.ActivationTokenService.MintResult;
import com.homekept.subscription.ActivationTokenService.ValidationResult;
import com.homekept.subscription.dto.ActivationValidateResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates the magic-link activation flow.
 *
 * <p>Domain crossings go only through service interfaces:
 * <ul>
 *   <li>booking → {@link BookingService} (never its repository or entity)</li>
 *   <li>identity → {@link AuthService}</li>
 *   <li>property → {@link PropertyService}</li>
 * </ul>
 *
 * <p>No PII in log statements — IDs and enums only.
 */
@Service
public class ActivationService {

    private static final Logger log = LoggerFactory.getLogger(ActivationService.class);

    private final ActivationTokenService activationTokenService;
    private final BookingService bookingService;
    private final AuthService authService;
    private final PropertyService propertyService;
    private final SubscriberRepository subscriberRepository;
    private final ActivationNotifier activationNotifier;

    public ActivationService(ActivationTokenService activationTokenService,
                             BookingService bookingService,
                             AuthService authService,
                             PropertyService propertyService,
                             SubscriberRepository subscriberRepository,
                             ActivationNotifier activationNotifier) {
        this.activationTokenService = activationTokenService;
        this.bookingService = bookingService;
        this.authService = authService;
        this.propertyService = propertyService;
        this.subscriberRepository = subscriberRepository;
        this.activationNotifier = activationNotifier;
    }

    /**
     * Validates a magic-link token without consuming it.
     * Returns firstName to let the frontend greet the prospective subscriber by name.
     * No PII in the "invalid" path — only a safe reason label.
     *
     * @param token the raw activation token from the magic link
     * @return validate response (valid+firstName or invalid+reason)
     */
    public ActivationValidateResponse validate(String token) {
        ValidationResult result = activationTokenService.validate(token);
        if (!result.valid()) {
            return ActivationValidateResponse.invalid(result.reason());
        }
        BookingActivationData data = bookingService.getActivationData(result.bookingId());
        return ActivationValidateResponse.valid(result.bookingId(), data.firstName());
    }

    /**
     * Completes the activation flow in a single transaction:
     * <ol>
     *   <li>Validates the password length.</li>
     *   <li>Validates and consumes the token (single-use).</li>
     *   <li>Loads the booking activation data.</li>
     *   <li>Creates the {@code User} (CUSTOMER, PENDING_ACTIVATION).</li>
     *   <li>Creates the {@code Property} from booking data.</li>
     *   <li>Creates the {@code Subscriber} (PENDING_ACTIVATION, MONTHLY).</li>
     *   <li>Links the property to the subscriber.</li>
     *   <li>Marks the booking as converted.</li>
     *   <li>Issues auth tokens (the subscriber is now signed in).</li>
     * </ol>
     *
     * <p>No PII in logs — IDs only.
     *
     * @param token       the raw activation token from the magic link
     * @param rawPassword the plaintext password chosen by the prospective subscriber
     * @return result containing userId and token pair for cookie-setting
     * @throws InvalidActivationRequestException if the password is null or shorter than 8 characters
     * @throws InvalidActivationTokenException   if the token is invalid, expired, or consumed
     */
    @Transactional
    public ActivationCompleteResult complete(String token, String rawPassword) {
        // 1. Validate password length before touching the token (fail fast, cheaper)
        if (rawPassword == null || rawPassword.length() < 8) {
            throw new InvalidActivationRequestException("Password must be at least 8 characters");
        }

        // 2. Validate and consume the token (throws InvalidActivationTokenException if bad)
        Long bookingId = activationTokenService.validateAndConsume(token);
        log.info("Activation token consumed bookingId={}", bookingId);

        // 3. Load booking data (crosses into booking domain via service)
        BookingActivationData d = bookingService.getActivationData(bookingId);

        // 4. Create the user account
        var user = authService.createUser(
                d.email(), rawPassword, d.firstName(), d.lastName(),
                Role.CUSTOMER, UserStatus.PENDING_ACTIVATION);
        log.info("Activation user created userId={} bookingId={}", user.getId(), bookingId);

        // 5. Create the property from booking address data
        var property = propertyService.createFromActivation(
                new PropertyService.CreatePropertyRequest(
                        d.streetAddress(),
                        d.city(),
                        d.postalCode(),
                        d.yearBuilt(),
                        d.squareFootageRange(),
                        d.propertyType() != null ? PropertyType.valueOf(d.propertyType()) : null
                ));
        log.info("Activation property created propertyId={} bookingId={}", property.getId(), bookingId);

        // 6. Create the subscriber row (initial status PENDING_ACTIVATION — set at construction,
        //    no state machine check needed for the initial state per arch doc)
        Subscriber subscriber = new Subscriber(
                user.getId(), property.getId(),
                SubscriberStatus.PENDING_ACTIVATION, BillingCycle.MONTHLY);
        subscriber = subscriberRepository.save(subscriber);
        log.info("Activation subscriber created subscriberId={} bookingId={}", subscriber.getId(), bookingId);

        // 7. Link the property to the subscriber (same transaction — deferrable FK fires at commit)
        propertyService.linkSubscriber(property.getId(), subscriber.getId());

        // 8. Mark the booking as converted
        bookingService.markConverted(bookingId, subscriber.getId());

        // 9. Issue auth tokens so the subscriber is immediately signed in
        TokenPair pair = authService.issueTokensFor(user);

        return new ActivationCompleteResult(user.getId(), pair.accessToken(), pair.refreshToken());
    }

    /**
     * Mints an activation token for the booking and sends the magic-link email.
     * Called by the admin invite endpoint after the walk-through is completed.
     * Does NOT log the raw token — only the booking id (per no-PII rule).
     *
     * @param bookingId the walk-through booking id
     */
    @Transactional
    public void sendInvite(Long bookingId) {
        MintResult mint = activationTokenService.mint(bookingId);
        bookingService.attachActivationToken(bookingId, mint.tokenId());

        BookingActivationData data = bookingService.getActivationData(bookingId);

        // sendActivationLink(email, rawToken, bookingId) — email is not logged by DefaultActivationNotifier
        activationNotifier.sendActivationLink(data.email(), mint.rawToken(), bookingId);

        log.info("Activation invite sent bookingId={} tokenId={}", bookingId, mint.tokenId());
    }

    // ── Result type ───────────────────────────────────────────────────────────

    /**
     * Carries the result of a successful activation completion back to the controller layer.
     */
    public record ActivationCompleteResult(Long userId, String accessToken, String refreshToken) {}
}
