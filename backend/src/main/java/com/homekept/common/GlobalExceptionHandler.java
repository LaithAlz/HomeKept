package com.homekept.common;

import com.homekept.booking.exception.BookingNotFoundException;
import com.homekept.booking.exception.IllegalBookingTransitionException;
import com.homekept.booking.exception.InvalidBookingRequestException;
import org.springframework.security.access.AccessDeniedException;
import com.homekept.identity.exception.AuthenticationException;
import com.homekept.identity.exception.RateLimitExceededException;
import com.homekept.identity.exception.TokenException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Centralised error handler. Returns structured error envelopes per the API contract:
 * <pre>
 * {
 *   "error": {
 *     "code": "...",
 *     "message": "...",
 *     "fields": { ... },    // present only for VALIDATION_FAILED
 *     "request_id": "req_..." // always present; echoes X-Request-Id if sent by the caller
 *   }
 * }
 * </pre>
 *
 * <p>No PII is logged — error messages use codes and generic text only.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String X_REQUEST_ID = "X-Request-Id";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorEnvelope> handleValidation(MethodArgumentNotValidException ex,
                                                          HttpServletRequest request) {
        Map<String, String> fields = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "invalid",
                        (a, b) -> a
                ));
        return ResponseEntity.badRequest()
                .body(ErrorEnvelope.of("VALIDATION_FAILED", "Validation failed", fields, requestId(request)));
    }

    /**
     * Generic auth failure — same response for unknown email AND wrong password.
     * The client must NOT be able to distinguish these (no user enumeration).
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorEnvelope> handleAuth(AuthenticationException ex,
                                                    HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorEnvelope.of("INVALID_CREDENTIALS", "Invalid email or password", requestId(request)));
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ErrorEnvelope> handleRateLimit(RateLimitExceededException ex,
                                                         HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ErrorEnvelope.of("RATE_LIMITED", "Too many attempts. Please wait and try again.", requestId(request)));
    }

    @ExceptionHandler(TokenException.class)
    public ResponseEntity<ErrorEnvelope> handleToken(TokenException ex,
                                                     HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorEnvelope.of("TOKEN_INVALID", "Session expired or invalid. Please log in again.", requestId(request)));
    }

    /** Booking not found (or not accessible) — 404 per ownership-failure rule. */
    @ExceptionHandler(BookingNotFoundException.class)
    public ResponseEntity<ErrorEnvelope> handleBookingNotFound(BookingNotFoundException ex,
                                                               HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorEnvelope.of("NOT_FOUND", "Booking not found", requestId(request)));
    }

    /** Illegal state machine transition — 409 Conflict per api-contract.md. */
    @ExceptionHandler(IllegalBookingTransitionException.class)
    public ResponseEntity<ErrorEnvelope> handleIllegalTransition(IllegalBookingTransitionException ex,
                                                                  HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorEnvelope.of("ILLEGAL_STATE_TRANSITION",
                        "Booking status transition " + ex.getFrom() + " → " + ex.getTo() + " is not permitted",
                        requestId(request)));
    }

    /**
     * Curated booking validation failure — invalid enum values, squareFootageRange, etc.
     * The message on {@link InvalidBookingRequestException} is safe to return verbatim
     * (it is set by the service using only whitelisted, pre-canned strings).
     */
    @ExceptionHandler(InvalidBookingRequestException.class)
    public ResponseEntity<ErrorEnvelope> handleInvalidBookingRequest(InvalidBookingRequestException ex,
                                                                      HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorEnvelope.of("INVALID_REQUEST", ex.getMessage(), requestId(request)));
    }

    /**
     * Authenticated-but-insufficient-role denial from method security
     * ({@code @PreAuthorize}) → 403. Without this explicit handler the catch-all below
     * would swallow the {@link AccessDeniedException} ({@code AuthorizationDeniedException}
     * extends it) thrown inside the controller and return 500 instead of 403. Anonymous
     * (unauthenticated) requests never reach here — they are short-circuited to 401 by the
     * security filter's authentication entry point. 403 = wrong role (per CLAUDE.md).
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorEnvelope> handleAccessDenied(AccessDeniedException ex,
                                                            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorEnvelope.of("FORBIDDEN", "Insufficient permissions", requestId(request)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorEnvelope> handleUnexpected(Exception ex,
                                                          HttpServletRequest request) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorEnvelope.of("INTERNAL_ERROR", "An unexpected error occurred", requestId(request)));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Returns the incoming X-Request-Id header value if present, otherwise generates
     * a short random request ID prefixed with "req_".
     */
    private String requestId(HttpServletRequest request) {
        String incoming = request.getHeader(X_REQUEST_ID);
        if (incoming != null && !incoming.isBlank()) {
            return incoming;
        }
        return "req_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    // ── Envelope types ────────────────────────────────────────────────────────

    public record ErrorEnvelope(Error error) {
        public record Error(String code, String message, Map<String, String> fields, String request_id) {}

        public static ErrorEnvelope of(String code, String message, String requestId) {
            return new ErrorEnvelope(new Error(code, message, null, requestId));
        }

        public static ErrorEnvelope of(String code, String message, Map<String, String> fields, String requestId) {
            return new ErrorEnvelope(new Error(code, message, fields, requestId));
        }
    }
}
