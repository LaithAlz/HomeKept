package com.homekept.visit;

import com.homekept.analytics.AnalyticsEvent;
import com.homekept.analytics.AnalyticsService;
import com.homekept.subscription.Subscriber;
import com.homekept.subscription.SubscriberQueryService;
import com.homekept.visit.exception.SubscriberNotActiveException;
import com.homekept.visit.dto.AppCreateTodoRequest;
import com.homekept.visit.dto.TodoResponse;
import com.homekept.visit.exception.VisitNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Customer-facing "your list" (to-do) endpoints (the app-side of the visit domain).
 *
 * <h2>Rules</h2>
 * <ul>
 *   <li>Every subscriber is resolved from the authenticated user id — never from a
 *       subscriber id passed by the caller (prevents IDOR).</li>
 *   <li>Ownership failures → 404, not 403 (don't leak existence). Reuses
 *       {@link VisitNotFoundException} for todo-not-found, matching the precedent already
 *       set by {@code TechVisitService#patchTodo} for the same entity.</li>
 *   <li>No PII in logs — the todo body is customer free text and is never logged (matches
 *       the {@code todo_added} analytics event, whose properties intentionally omit it).</li>
 * </ul>
 *
 * <h2>How a customer todo reaches the technician (MVP)</h2>
 * <p>A new item starts {@code OPEN} with no {@code visitId}. There is currently no job that
 * flips an OPEN item to {@code SCHEDULED} + sets {@code visitId} when a visit is confirmed
 * (the arch doc describes this as folding into a {@code VisitService(source=TODO)} row, which
 * would require a catalog {@code service_id} that a free-text todo doesn't have — this needs
 * further design and is not part of this slice). In the meantime, the already-shipped
 * {@code PATCH /api/tech/todos/{id}} lets a technician resolve (DONE/DECLINED) any OPEN todo
 * belonging to a subscriber for whom they have an active (SCHEDULED/IN_PROGRESS) visit,
 * independent of {@code visitId} — so a customer's list item is already actionable by the
 * technician on the subscriber's next visit without the fold step being built. Surfacing the
 * subscriber's OPEN todos on the day sheet ({@code GET /api/tech/visits/today}) so the
 * technician sees them without looking them up separately is tracked separately
 * (coordinate with #108).
 *
 * <h2>Domain boundary</h2>
 * <p>Resolves the subscriber via {@link SubscriberQueryService} (subscription domain's
 * service) — never by calling the subscription repository directly.
 */
@Service
public class TodoAppService {

    private static final Logger log = LoggerFactory.getLogger(TodoAppService.class);

    private final TodoItemRepository todoItemRepository;
    private final SubscriberQueryService subscriberQueryService;
    private final AnalyticsService analytics;

    public TodoAppService(TodoItemRepository todoItemRepository,
                          SubscriberQueryService subscriberQueryService,
                          AnalyticsService analytics) {
        this.todoItemRepository = todoItemRepository;
        this.subscriberQueryService = subscriberQueryService;
        this.analytics = analytics;
    }

    /**
     * Returns the authenticated customer's todo items ("your list"), newest first.
     *
     * @param userId authenticated user id (from JWT principal)
     */
    @Transactional(readOnly = true)
    public List<TodoResponse> listTodos(Long userId) {
        Long subscriberId = resolveSubscriberId(userId);
        return todoItemRepository.findBySubscriberIdOrderByCreatedAtDesc(subscriberId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Adds a new OPEN item to the authenticated customer's list.
     *
     * @param userId  authenticated user id (from JWT principal)
     * @param request the item's free-text body
     * @return the created item
     */
    @Transactional
    public TodoResponse createTodo(Long userId, AppCreateTodoRequest request) {
        Subscriber subscriber = subscriberQueryService.findByUserId(userId)
                // No subscriber → ownership rule → 404, never 403.
                .orElseThrow(() -> new VisitNotFoundException(-1L));
        // Don't let a non-paying (paused/cancelled) customer keep queueing to-do items.
        // Serviceable = ACTIVE or PAYMENT_ISSUE — same policy as visit-start and reschedule.
        if (!subscriber.getStatus().isServiceable()) {
            log.info("todo_create_blocked userId={} subscriberStatus={}", userId, subscriber.getStatus());
            throw new SubscriberNotActiveException(subscriber.getStatus().name());
        }
        Long subscriberId = subscriber.getId();
        TodoItem saved = todoItemRepository.save(new TodoItem(subscriberId, request.body()));

        // "todo_added" analytics event (arch doc §5.7) — the body is free text and is
        // intentionally never logged or sent to analytics, so the event carries no
        // properties. Attributed to the customer; fires after this transaction commits.
        log.info("todo_added todoId={} subscriberId={}", saved.getId(), subscriberId);
        analytics.capture(userId, AnalyticsEvent.TODO_ADDED, Map.of());

        return toResponse(saved);
    }

    /**
     * Removes an item from the authenticated customer's list.
     * Returns 404 if the item does not exist or does not belong to this customer
     * (ownership-failure rule — 404, not 403).
     *
     * @param userId authenticated user id (from JWT principal)
     * @param todoId the todo item id
     */
    @Transactional
    public void deleteTodo(Long userId, Long todoId) {
        Long subscriberId = resolveSubscriberId(userId);
        TodoItem todo = todoItemRepository.findByIdAndSubscriberId(todoId, subscriberId)
                .orElseThrow(() -> {
                    log.debug("todo_not_found_or_not_owned todoId={} subscriberId={}", todoId, subscriberId);
                    return new VisitNotFoundException(todoId);
                });
        todoItemRepository.delete(todo);
        log.info("todo_removed todoId={} subscriberId={}", todoId, subscriberId);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Resolves the subscriber id from the authenticated user id.
     * Returns 404 if no subscriber row exists for this user.
     */
    private Long resolveSubscriberId(Long userId) {
        return subscriberQueryService.findByUserId(userId)
                .map(s -> s.getId())
                // No subscriber → treat as not-found (ownership rule → 404, never 403).
                .orElseThrow(() -> new VisitNotFoundException(-1L));
    }

    private TodoResponse toResponse(TodoItem t) {
        return new TodoResponse(
                t.getId(),
                t.getSubscriberId(),
                t.getBody(),
                t.getStatus().name(),
                t.getVisitId(),
                t.getDeclineNote(),
                t.getCreatedAt(),
                t.getUpdatedAt()
        );
    }
}
