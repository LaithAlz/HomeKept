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
-- 2. NOTE READS. property_note already has (property_id, created_at DESC) from V18.
--    visit_note has only V7's plain (visit_id), with no created_at, so the newest-first
--    read has to sort. Adding the matching composite makes the two note tables behave
--    identically, which is the whole point of having given them the same shape.
--
-- Both include created_at DESC so the ordered read can be served straight from the index,
-- which also matters for the cursor pagination these endpoints need: a stable cursor
-- requires a total order, and these are the columns it orders on.
-- ─────────────────────────────────────────────────────────────────────────────

-- Serves existsAttendingAssignment, the technician note-access check.
CREATE INDEX idx_visit_property_technician ON visit (property_id, technician_id);

-- Serves "this visit's notes, newest first", matching idx_property_note_property.
CREATE INDEX idx_visit_note_visit_id ON visit_note (visit_id, id DESC);
