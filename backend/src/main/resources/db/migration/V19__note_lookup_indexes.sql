-- V19__note_lookup_indexes.sql
-- Indexes for the two lookups the notes feature added, both of which currently have no
-- supporting index at all.
--
-- 1. THE AUTHORIZATION CHECK. Technician access to a property's notes is decided by
--    "does this technician have a visit at this property", i.e. a predicate on
--    visit (property_id, technician_id). There is no index on visit.property_id anywhere:
--    V6 indexes subscriber_id, scheduled_for and status, and V7 adds technician_id. So the
--    check falls back to idx_visit_technician and filters, meaning it scans every visit a
--    technician has ever been assigned on EVERY property-note request, including every
--    request it is about to deny. An authorization predicate is the last thing that should
--    get slower as the business grows, and denials are exactly the traffic you get when
--    something is probing.
--
-- 2. NOTE READS. visit_note has only V7's plain (visit_id), so the newest-first read has to
--    sort. This adds the composite that matches how notes are actually read and paged, and
--    matches V18's property_note index so the two tables behave identically.
--
-- The note index is (visit_id, id DESC), NOT (visit_id, created_at DESC). Notes order and
-- cursor on id alone: for an append-only log insert order is log order, and id is the only
-- key that is both unique and monotonic, so it is the only one a keyset cursor can partition
-- exactly without risking a skipped row. Indexing created_at would serve a sort the code
-- deliberately does not perform. See VisitNoteService's class javadoc for why ordering on
-- created_at while cursoring on id could silently lose a note.
-- ─────────────────────────────────────────────────────────────────────────────

-- Serves existsAttendingAssignment, the technician note-access check.
CREATE INDEX idx_visit_property_technician ON visit (property_id, technician_id);

-- Serves "this visit's notes, newest first", matching idx_property_note_property.
CREATE INDEX idx_visit_note_visit_id ON visit_note (visit_id, id DESC);
