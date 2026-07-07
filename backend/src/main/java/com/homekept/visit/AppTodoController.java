package com.homekept.visit;

import com.homekept.visit.dto.AppCreateTodoRequest;
import com.homekept.visit.dto.TodoResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Customer-facing "your list" endpoints (role: CUSTOMER).
 *
 * <p>The authenticated user's subscriber is resolved inside the service from the JWT
 * principal (a {@code Long} user id set by {@link com.homekept.identity.JwtAuthFilter}).
 * The caller never supplies a subscriber id — preventing IDOR.
 *
 * <p>These endpoints fall under {@code .anyRequest().authenticated()} in SecurityConfig;
 * they are NOT in the public allowlist. The {@code @PreAuthorize} annotation is the second
 * role gate.
 *
 * <p>Ownership failures → 404 (not 403). 403 = wrong role.
 */
@RestController
@RequestMapping("/api/app/todos")
@PreAuthorize("hasRole('CUSTOMER')")
public class AppTodoController {

    private final TodoAppService todoAppService;

    public AppTodoController(TodoAppService todoAppService) {
        this.todoAppService = todoAppService;
    }

    /**
     * GET /api/app/todos?propertyId=
     *
     * <p>Returns the authenticated customer's todo items ("your list"), newest first.
     *
     * @param propertyId optional property to scope to (multi-property portfolio); see
     *                   {@link com.homekept.subscription.SubscriberQueryService#resolveOwnedSubscriber}
     * @param auth       JWT principal — Long user id
     */
    @GetMapping
    public ResponseEntity<List<TodoResponse>> listTodos(
            @RequestParam(required = false) Long propertyId,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(todoAppService.listTodos(userId, propertyId));
    }

    /**
     * POST /api/app/todos?propertyId=
     *
     * <p>Adds a new OPEN item to the authenticated customer's list.
     *
     * @param propertyId optional property to scope to (multi-property portfolio)
     * @param request    {@code {body}}
     * @param auth       JWT principal — Long user id
     */
    @PostMapping
    public ResponseEntity<TodoResponse> createTodo(
            @RequestParam(required = false) Long propertyId,
            @Valid @RequestBody AppCreateTodoRequest request,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        TodoResponse response = todoAppService.createTodo(userId, propertyId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * DELETE /api/app/todos/{id}?propertyId=
     *
     * <p>Removes an item from the authenticated customer's list. Returns 404 if the item
     * does not belong to the authenticated customer (ownership → 404, not 403).
     *
     * @param id         the todo item id
     * @param propertyId optional property to scope to (multi-property portfolio)
     * @param auth       JWT principal — Long user id
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTodo(
            @PathVariable Long id,
            @RequestParam(required = false) Long propertyId,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        todoAppService.deleteTodo(userId, propertyId, id);
        return ResponseEntity.noContent().build();
    }
}
