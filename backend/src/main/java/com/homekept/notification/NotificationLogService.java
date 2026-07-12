package com.homekept.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Insert-blind dedupe for {@link NotificationLog} (#89) — mirrors the partial-unique-index
 * guard {@code RescheduleService.createRequest} uses for PENDING reschedule requests: attempt
 * the insert, and treat a unique-constraint violation as "already recorded" rather than
 * pre-checking existence with a separate query. This avoids a race between the check and the
 * insert across overlapping scheduler ticks or (future) multiple app instances.
 *
 * <h2>Why this method is deliberately NOT {@code @Transactional}</h2>
 * <p>Catching {@link DataIntegrityViolationException} from a failed {@code saveAndFlush} and
 * then returning normally from the <em>same</em> {@code @Transactional} method does not work:
 * Hibernate marks the persistence context (and the Spring-managed transaction wrapping it)
 * rollback-only the moment the flush fails, regardless of whether application code catches
 * the translated exception. The method would appear to "succeed" but throw
 * {@link org.springframework.transaction.UnexpectedRollbackException} on commit. The safe
 * options are (a) stay non-transactional so each write runs in its own isolated transaction,
 * or (b) if transactional, re-throw a different exception to force a clean rollback rather
 * than catch-and-return. {@code RescheduleService.createRequest} is {@code @Transactional} and
 * catches the violation but takes route (b): it re-throws a {@code RescheduleRequestConflictException}
 * (→ rollback → 409) instead of returning normally. {@code StripeWebhookService.persistEvent}
 * lets the violation propagate. This method needs to RETURN a boolean (first-time vs already-sent),
 * so route (b) is unavailable and it takes route (a): by staying plain (no {@code @Transactional}),
 * {@code repository.saveAndFlush(...)} runs in Spring Data's own self-contained transaction
 * for that single call, which rolls back cleanly and in isolation on a constraint violation —
 * leaving nothing here for a doomed outer transaction to poison.
 */
@Service
public class NotificationLogService {

    private static final Logger log = LoggerFactory.getLogger(NotificationLogService.class);

    private final NotificationLogRepository repository;

    public NotificationLogService(NotificationLogRepository repository) {
        this.repository = repository;
    }

    /**
     * Records that {@code notificationType} was sent for {@code targetType}/{@code targetId},
     * unless it was already recorded.
     *
     * @param notificationType the kind of reminder
     * @param targetType       the kind of entity {@code targetId} refers to
     * @param targetId         the target entity's id
     * @return {@code true} if this call is the first to record this reminder (the caller
     *         should send it now); {@code false} if it was already recorded (the caller
     *         should skip — it was already sent, by this run or an earlier one)
     */
    public boolean recordIfFirst(NotificationType notificationType, NotificationTargetType targetType,
            Long targetId) {
        try {
            repository.saveAndFlush(new NotificationLog(notificationType, targetType, targetId));
            return true;
        } catch (DataIntegrityViolationException e) {
            log.debug("notification_already_recorded type={} targetType={} targetId={}",
                    notificationType, targetType, targetId);
            return false;
        }
    }
}
