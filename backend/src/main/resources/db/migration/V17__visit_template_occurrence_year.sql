-- V17__visit_template_occurrence_year.sql
-- Records which yearly occurrence of a visit template a visit was created for, so the
-- top-up scheduler's idempotency guard stops depending on where the visit is currently
-- scheduled.
--
-- WHY THIS IS NEEDED NOW. VisitSchedulingService skips a template only if the subscriber
-- already has a visit for it whose scheduled_for sits inside the rolling 4-month lookahead
-- window. That held while a reschedule *added* a replacement row and left the original in
-- place at its original in-window time: one of the two always matched.
--
-- V16 changed reschedule to move the single row. Now a customer asking to push an October
-- visit to April moves it out of the window, the guard goes false on the next nightly run,
-- and the scheduler creates a SECOND visit for the same October occurrence. The customer
-- gets double-booked, a technician is dispatched to a visit the customer believes was
-- moved, and the admin list grows exactly the extra row the in-place change existed to
-- remove.
--
-- Widening the guard's upper bound instead would trade this for a worse bug: a visit moved
-- far forward would then suppress NEXT year's occurrence of the same template, silently
-- costing the customer a visit they paid for.
--
-- The occurrence is (template, year). Templates are one-per-calendar-month, so a template
-- occurs at most once a year and the pair is unique. This column is set once at creation
-- and never touched by a reschedule, which is the whole point: it identifies which visit
-- the row is, not when it currently sits.
--
-- NULLABLE on purpose. Rows predating this migration have no recorded occurrence, and
-- backfilling every historical visit is not required for correctness: the guard only ever
-- looks at templates inside the current window. The backfill below covers the rows that
-- could matter, and the scheduler treats NULL as "no recorded occurrence".
-- ─────────────────────────────────────────────────────────────────────────────
ALTER TABLE visit
    ADD COLUMN template_occurrence_year INTEGER;

-- Backfill from where each visit currently sits. Correct for every existing row: no visit
-- has yet been rescheduled under the in-place model (V16 ships alongside this migration),
-- so scheduled_for is still the occurrence it was created for. Rendered in Toronto rather
-- than UTC because placeholders are generated at local noon, and a UTC year boundary would
-- misfile a visit scheduled in the first hours of January.
UPDATE visit
SET template_occurrence_year =
        EXTRACT(YEAR FROM (scheduled_for AT TIME ZONE 'America/Toronto'))::INTEGER
WHERE visit_template_id IS NOT NULL;

-- Serves the guard's exact lookup: does this subscriber already have a visit for this
-- template's occurrence in this year.
CREATE INDEX idx_visit_subscriber_template_occurrence
    ON visit (subscriber_id, visit_template_id, template_occurrence_year);
