package com.homekept.visit;

import com.homekept.common.Pagination;
import com.homekept.identity.UserQueryService;
import com.homekept.identity.UserQueryService.UserSummary;
import com.homekept.visit.dto.VisitNoteItem;
import com.homekept.visit.dto.VisitNotePage;
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
 *
 * <h2>Pagination, not a fixed cap</h2>
 * <p>An earlier version of this class capped {@link #listNotes} at a fixed 100 rows with no
 * way to page further. That made "append-only, a correction is a new note" false in
 * practice: a technician who wanted a note gone could post enough filler notes to push it
 * past the cap, permanently unreachable through any API in the system — worse than a delete
 * endpoint, which would at least be attributable and auditable. {@link #listNotes} now takes
 * a real {@code cursor}/{@code limit} (same convention as {@code VisitAdminService#listVisits}
 * — an exclusive-upper-bound {@code id} cursor), so every note stays reachable no matter how
 * many pile up.
 *
 * <p><b>Ordering is {@code id DESC}, and the cursor is {@code id}. Those being the same key
 * is the point.</b> For an append-only log, insert order is log order, and {@code id} is a
 * unique monotonic identity column, so this is a total order that a keyset cursor
 * ({@code WHERE id < :cursor}) partitions exactly: every row falls in exactly one page and
 * none can be skipped or repeated.
 *
 * <p>An earlier version ordered by {@code createdAt DESC, id DESC} while still cursoring on
 * {@code id} alone, and claimed that was sound because "id ranges are disjoint by
 * construction". That claim was false, and the reason is worth recording so nobody
 * reintroduces it. {@code createdAt} is {@code @CreationTimestamp} with the default
 * {@code SourceType.VM}, so Hibernate stamps it in the JVM at flush; {@code id} is
 * {@code IDENTITY}, so Postgres assigns it when the INSERT actually executes. Between those
 * two moments sits a JDBC round trip. Two concurrent writers can therefore produce a row
 * with a <em>higher id and an older timestamp</em> with no clock skew involved, just a
 * scheduler. The cursor is set from the last row in display order, which under such an
 * inversion is not the page's minimum id, so the next page's {@code id < cursor} window
 * skips the inverted row entirely: returned by no page, ever.
 *
 * <p>That is precisely the failure this pagination exists to prevent, in a record whose
 * whole value is that nothing said in it disappears. Ordering by the cursor key removes the
 * possibility rather than narrowing it, and it costs only that display order is insert order
 * rather than timestamp order, which differ only under an inversion, by microseconds, and
 * arguably the former is the more honest ordering for a log.
 */
@Service
class VisitNoteService {

    private static final Logger log = LoggerFactory.getLogger(VisitNoteService.class);

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final VisitNoteRepository visitNoteRepository;
    private final UserQueryService userQueryService;

    VisitNoteService(VisitNoteRepository visitNoteRepository, UserQueryService userQueryService) {
        this.visitNoteRepository = visitNoteRepository;
        this.userQueryService = userQueryService;
    }

    /**
     * Returns one cursor-paginated page of a visit's notes, newest first, with each note's
     * author resolved to a name via a single batched
     * {@link UserQueryService#findSummariesByIds} call for the whole page — never one query
     * per note.
     *
     * <p>Fetches {@code limit + 1} rows so a further page can be detected (and
     * {@code nextCursor} populated) without a separate {@code COUNT} query — a count would
     * also be racy against concurrent inserts, where an over-fetch is not.
     *
     * @param visitId      the visit id (existence/ownership already verified by the caller)
     * @param cursor       optional {@code id} cursor (exclusive upper bound); {@code null}
     *                     for the first page
     * @param limit        optional page size (defaults to {@value #DEFAULT_PAGE_SIZE}, capped
     *                     at {@value #MAX_PAGE_SIZE})
     * @param readerUserId the authenticated principal reading this page — logged, not used
     *                     for authorization (the caller already did that)
     */
    @Transactional(readOnly = true)
    VisitNotePage listNotes(Long visitId, Long cursor, Integer limit, Long readerUserId) {
        int pageSize = Pagination.resolveLimit(limit, DEFAULT_PAGE_SIZE, MAX_PAGE_SIZE);
        PageRequest pageable = PageRequest.of(0, pageSize + 1);

        List<VisitNote> rows = (cursor != null)
                ? visitNoteRepository.findByVisitIdAndIdLessThanOrderByIdDesc(visitId, cursor, pageable)
                : visitNoteRepository.findByVisitIdOrderByIdDesc(visitId, pageable);

        boolean hasMore = rows.size() > pageSize;
        List<VisitNote> page = hasMore ? rows.subList(0, pageSize) : rows;
        Long nextCursor = hasMore ? page.get(page.size() - 1).getId() : null;

        // A successful read leaves a trace too (not just writes) — resource id, reader id,
        // and a count only; no bodies, no other PII — so an over-broad grant is at least
        // forensically visible.
        log.info("visit_notes_read visitId={} readerUserId={} count={}", visitId, readerUserId, page.size());

        if (page.isEmpty()) {
            return new VisitNotePage(List.of(), null);
        }

        List<Long> authorIds = page.stream().map(VisitNote::getAuthorUserId).distinct().toList();
        Map<Long, UserSummary> authorsById = userQueryService.findSummariesByIds(authorIds);

        List<VisitNoteItem> items = page.stream().map(n -> toItem(n, authorsById)).collect(Collectors.toList());
        return new VisitNotePage(items, nextCursor);
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
     * is a new note, not erasing the old one. Pagination (see {@link #listNotes}) is what
     * makes that guarantee actually hold — see this class's javadoc.
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
