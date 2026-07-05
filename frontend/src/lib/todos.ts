/**
 * TanStack Query hooks for the customer-facing "your list" (to-do) endpoints
 * (`backend/src/main/java/com/homekept/visit/AppTodoController.java`).
 *
 * Field names below mirror `backend/src/main/java/com/homekept/visit/dto/TodoResponse.java`
 * and `AppCreateTodoRequest.java` verbatim. Status values mirror
 * `backend/src/main/java/com/homekept/visit/TodoItemStatus.java` verbatim.
 */

import {
  useMutation,
  useQuery,
  useQueryClient,
  type UseMutationResult,
  type UseQueryResult,
} from "@tanstack/react-query";
import { del, get, post } from "@/lib/api";

export type TodoItemStatus = "OPEN" | "SCHEDULED" | "DONE" | "DECLINED";

/** See `TodoResponse.java`. */
export interface TodoResponse {
  id: number;
  subscriberId: number;
  body: string;
  status: TodoItemStatus;
  visitId: number | null;
  declineNote: string | null;
  createdAt: string; // ISO instant (UTC)
  updatedAt: string; // ISO instant (UTC)
}

const TODOS_KEY = ["app-todos"] as const;

/** GET /api/app/todos — the authenticated customer's "your list" items, newest first. */
export function useTodos(): UseQueryResult<TodoResponse[]> {
  return useQuery({
    queryKey: TODOS_KEY,
    queryFn: () => get<TodoResponse[]>("/api/app/todos"),
  });
}

/** POST /api/app/todos — adds a new OPEN item to the authenticated customer's list. */
export function useCreateTodo(): UseMutationResult<TodoResponse, unknown, string> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: string) => post<TodoResponse>("/api/app/todos", { body }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: TODOS_KEY }),
  });
}

/** DELETE /api/app/todos/{id} — removes an item from the authenticated customer's list. */
export function useDeleteTodo(): UseMutationResult<void, unknown, number> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => del<void>(`/api/app/todos/${id}`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: TODOS_KEY }),
  });
}
