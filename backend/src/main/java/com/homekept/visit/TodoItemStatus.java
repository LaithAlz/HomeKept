package com.homekept.visit;

/**
 * Lifecycle status of a {@link TodoItem} ("your list" item).
 *
 * <ul>
 *   <li>{@code OPEN} — submitted by subscriber; not yet folded into a visit</li>
 *   <li>{@code SCHEDULED} — folded into an upcoming visit as a checklist item</li>
 *   <li>{@code DONE} — completed by the technician</li>
 *   <li>{@code DECLINED} — technician couldn't or shouldn't do it;
 *       {@code TodoItem.declineNote} explains why</li>
 * </ul>
 */
public enum TodoItemStatus {
    OPEN,
    SCHEDULED,
    DONE,
    DECLINED
}
