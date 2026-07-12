package com.homekept.notification;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link NotificationLog}.
 *
 * <p>{@link NotificationLogService#recordIfFirst} is the usual entry point (it owns the
 * insert-blind dedupe pattern). {@link #existsByNotificationTypeAndTargetTypeAndTargetId} is
 * exposed for tests that want to assert a ledger row was (or was not) written.
 */
public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {

    boolean existsByNotificationTypeAndTargetTypeAndTargetId(
            NotificationType notificationType, NotificationTargetType targetType, Long targetId);
}
