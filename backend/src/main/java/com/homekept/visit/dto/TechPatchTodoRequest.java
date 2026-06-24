package com.homekept.visit.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Request body for {@code PATCH /api/tech/todos/{id}}.
 * The technician can only move a todo to DONE or DECLINED in the field.
 */
public record TechPatchTodoRequest(
        @NotNull
        @Pattern(regexp = "DONE|DECLINED",
                 message = "status must be DONE or DECLINED")
        String status,

        /** Required when status = DECLINED. Explains why the item was declined. */
        String note
) {}
