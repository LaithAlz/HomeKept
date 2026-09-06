package com.homekept.visit;

import com.homekept.identity.UserQueryService;
import com.homekept.identity.UserQueryService.UserSummary;
import com.homekept.visit.dto.VisitNoteItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Shared note-log logic for a visit, used by both {@link VisitAdminService} (the admin
 * console) and {@link TechVisitService} (the assigned technician) — same underlying
 * {@code visit_note} rows, same mapping/author-resolution behaviour, regardless of which
 * role is reading or writing.
 *
 * <p>Package-private: this class does NOT enforce access control. Each caller is
 * responsible for confirming the visit exists (and, for a technician, that it is assigned to
 * them) BEFORE calling in here — see {@link VisitAdminService#listNotes} and
 * {@link TechVisitService#requireOwnedVisit}. This mirrors how {@code VisitNoteRepository}
 * itself carries no authz either; the authz boundary is the two callers' controllers/services,
 * not this shared helper.
 */
@Service
class VisitNoteService {

    private static final Logger log = LoggerFactory.getLogger(VisitNoteService.class);

    /** Cap on notes returned per visit — mirrors {@code VisitAdminService.EVENTS_LIMIT}. */
    private static final int NOTES_LIMIT = 100;

    private final VisitNoteRepository visitNoteRepository;
    private final UserQueryService userQueryService;

    VisitNoteService(VisitNoteRepository visitNoteRepository, UserQueryService userQueryService) {
        this.visitNoteRepository = visitNoteRepository;
        this.userQueryService = userQueryService;
    }

    /**
     * Returns a visit's notes, newest first, capped at {@value #NOTES_LIMIT} rows, with each
     * note's author resolved to a name via a single batched
     * {@link UserQueryService#findSummariesByIds} call for the whole page — never one query
     * per note.
     *
     * @param visitId the visit id (existence/ownership already verified by the caller)
     */
    @Transactional(readOnly = true)
    List<VisitNoteItem> listNotes(Long visitId) {
        List<VisitNote> notes = visitNoteRepository
                .findByVisitIdOrderByCreatedAtDesc(visitId, PageRequest.of(0, NOTES_LIMIT));
        if (notes.isEmpty()) {
            return List.of();
        }

        List<Long> authorIds = notes.stream().map(VisitNote::getAuthorUserId).distinct().toList();
        Map<Long, UserSummary> authorsById = userQueryService.findSummariesByIds(authorIds);

        return notes.stream().map(n -> toItem(n, authorsById)).collect(Collectors.toList());
    }

    /**
     * Adds a note to a visit's operational log.
     *
     * <h2>Why there is no delete (or edit) operation</h2>
     * <p>This is deliberately an append-only log, the same shape as {@code visit_event} and
     * {@code subscription_event} (neither of which has a delete endpoint either), and the
     * same reasoning the sibling {@code property_note} migration states explicitly: a note
     * exists because it was operationally relevant to someone at the time — "the customer
     * asked us to skip the shed", "furnace filter sits behind the stairs" — and another
     * admin or technician may rely on that note still being there weeks or months later.
     * Deleting it would remove that history silently, with no trace anything was ever said,
     * which is worse than an outdated or mistaken note staying visible (with its timestamp
     * and author, which let a reader judge its currency). If a note is wrong, the correction
     * is a new note, not erasing the old one.
     *
     * @param visitId      the visit to add a note to (existence/ownership already verified
     *                     by the caller)
     * @param body         the note text (already validated non-blank, max length, at the DTO
     *                     boundary)
     * @param authorUserId the authenticated principal's user id — NEVER taken from request
     *                     input
     * @return the created note, with the author's name resolved
     */
    @Transactional
    VisitNoteItem addNote(Long visitId, String body, Long authorUserId) {
        VisitNote saved = visitNoteRepository.save(new VisitNote(visitId, authorUserId, body));

        log.info("visit_note_added visitId={} noteId={}", visitId, saved.getId());

        Map<Long, UserSummary> authorsById = userQueryService.findSummariesByIds(List.of(authorUserId));
        return toItem(saved, authorsById);
    }

    private VisitNoteItem toItem(VisitNote note, Map<Long, UserSummary> authorsById) {
        UserSummary author = authorsById.get(note.getAuthorUserId());
        return new VisitNoteItem(
                note.getId(),
                note.getBody(),
                note.getCreatedAt(),
                note.getAuthorUserId(),
                author != null ? author.firstName() : null,
                author != null ? author.lastName() : null);
    }
}
