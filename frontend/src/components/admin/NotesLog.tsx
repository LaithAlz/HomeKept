/**
 * Reusable threaded operational-notes log: a real list of append-only notes, newest
 * first, plus a labelled form to add to it. Backs the visit detail page's notes section
 * (`GET`/`POST /api/admin/visits/{id}/notes`) and is written generically enough to back a
 * future property-notes section too (`GET`/`POST /api/admin/properties/{propertyId}/notes`
 * returns the identical `AdminNoteItem`/`AdminNotePage` shape — see `@/lib/admin`). Every
 * string that names *what kind* of notes this is (`description`/`emptyMessage`/`logLabel`/
 * `formLabel`) is caller-supplied, not hardcoded here, so a property-notes page can reuse
 * this component unmodified.
 *
 * Deliberately has no edit or delete control anywhere in this component, and the hint
 * text never uses the word "delete": there is no edit/delete endpoint for a note (visit
 * or property) — it is append-only by design. A correction is posted as a new note.
 */
import { useId, useState, type FormEvent, type ReactNode } from "react";
import { Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { PanelLoading, PanelError } from "@/components/admin/PanelStates";
import { formatDateTime } from "@/lib/format";
import { ApiError } from "@/lib/api";
import { noteAuthorLabel, NOTE_BODY_MAX_LENGTH, type AdminNoteItem } from "@/lib/admin";

export interface NotesLogProps {
  /** Every note fetched so far, across however many pages have been loaded, newest first. */
  notes: AdminNoteItem[];
  /** True only for the first page's initial fetch (not subsequent "Load more" pages). */
  isLoading: boolean;
  isError: boolean;
  onRetry: () => void;
  /** Whether the backend's own cursor says another page exists (never a client-side guess). */
  hasMore: boolean;
  onLoadMore: () => void;
  isLoadingMore: boolean;
  /** True if the most recent "Load more" attempt itself failed. */
  loadMoreError?: boolean;
  onAddNote: (body: string) => Promise<unknown>;
  isAdding: boolean;
  /** Explains what this log is and how it differs from the resource's own one-time notes field. */
  description: ReactNode;
  /** Shown instead of the list when there are zero notes. */
  emptyMessage: string;
  /** Accessible label on the note list, e.g. "Visit notes". */
  logLabel: string;
  /** Label for the add-note textarea, e.g. "Add a note about this visit". */
  formLabel: string;
}

export function NotesLog({
  notes,
  isLoading,
  isError,
  onRetry,
  hasMore,
  onLoadMore,
  isLoadingMore,
  loadMoreError = false,
  onAddNote,
  isAdding,
  description,
  emptyMessage,
  logLabel,
  formLabel,
}: NotesLogProps) {
  const baseId = useId();
  const textareaId = `${baseId}-body`;
  const errorId = `${baseId}-error`;
  const hintId = `${baseId}-hint`;

  const [body, setBody] = useState("");
  const [submitError, setSubmitError] = useState<string | null>(null);

  async function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const trimmed = body.trim();
    if (!trimmed) {
      setSubmitError("Write something before adding a note.");
      return;
    }
    setSubmitError(null);
    try {
      await onAddNote(trimmed);
      setBody("");
    } catch (err) {
      setSubmitError(
        err instanceof ApiError ? err.message : "That didn't go through. Please try again.",
      );
    }
  }

  return (
    <div className="space-y-4">
      <p className="text-xs text-muted-foreground">{description}</p>

      <form onSubmit={(e) => void handleSubmit(e)} noValidate className="space-y-1.5">
        <Label htmlFor={textareaId}>{formLabel}</Label>
        <Textarea
          id={textareaId}
          value={body}
          onChange={(e) => setBody(e.target.value.slice(0, NOTE_BODY_MAX_LENGTH))}
          maxLength={NOTE_BODY_MAX_LENGTH}
          rows={3}
          disabled={isAdding}
          aria-describedby={submitError ? `${hintId} ${errorId}` : hintId}
        />
        <div className="flex items-start justify-between gap-3">
          <p id={hintId} className="text-xs text-muted-foreground">
            Up to {NOTE_BODY_MAX_LENGTH} characters. This is a permanent record: a note can't be
            edited once it's posted. Add a new note for a correction.
          </p>
          <p className="shrink-0 text-xs text-muted-foreground" aria-hidden="true">
            {body.length}/{NOTE_BODY_MAX_LENGTH}
          </p>
        </div>
        {submitError && (
          <p id={errorId} role="alert" className="text-xs text-destructive">
            {submitError}
          </p>
        )}
        <div className="flex justify-end">
          <Button type="submit" size="sm" disabled={isAdding || body.trim().length === 0}>
            {isAdding && <Loader2 className="size-3.5 animate-spin" aria-hidden="true" />}
            Add note
          </Button>
        </div>
      </form>

      <div className="border-t border-border pt-4">
        {isLoading && <PanelLoading label="Loading notes." className="p-0" />}

        {isError && !isLoading && (
          <PanelError label="We couldn't load the notes." onRetry={onRetry} className="p-0" />
        )}

        {!isLoading && !isError && notes.length === 0 && (
          <p className="text-xs text-muted-foreground">{emptyMessage}</p>
        )}

        {!isLoading && !isError && notes.length > 0 && (
          <>
            <ul className="space-y-3" role="list" aria-label={logLabel}>
              {notes.map((note) => (
                <li key={note.id} className="border-l-2 border-border pl-3 text-sm">
                  <p className="whitespace-pre-wrap break-words text-foreground">{note.body}</p>
                  <p className="mt-1 text-xs text-muted-foreground">
                    {noteAuthorLabel(note)} · {formatDateTime(note.createdAt)}
                  </p>
                </li>
              ))}
            </ul>

            <div className="mt-3">
              {hasMore ? (
                <>
                  <Button
                    type="button"
                    size="sm"
                    variant="outline"
                    disabled={isLoadingMore}
                    onClick={onLoadMore}
                  >
                    {isLoadingMore && (
                      <Loader2 className="size-3.5 animate-spin" aria-hidden="true" />
                    )}
                    Load more notes
                  </Button>
                  {loadMoreError && (
                    <p role="alert" className="mt-1 text-xs text-destructive">
                      We couldn't load more notes. Try again.
                    </p>
                  )}
                </>
              ) : (
                <p className="text-xs text-muted-foreground">That's every note on record.</p>
              )}
            </div>
          </>
        )}
      </div>
    </div>
  );
}
