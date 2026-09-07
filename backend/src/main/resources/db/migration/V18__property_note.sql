-- V18__property_note.sql
-- Technician notes about a PROPERTY, kept for tracking across visits.
--
-- Mirrors visit_note (V7) deliberately: same id / entity_id / author_user_id / body /
-- created_at shape, so the two note surfaces read, paginate and render the same way.
--
-- WHY THIS AND NOT A COLUMN ON property. An earlier draft of this migration added a single
-- `property.notes TEXT` an admin could edit. That was the wrong shape for what these notes
-- are for. A technician writing "furnace filter sits behind the stairs" or "back gate
-- sticks, lift while pushing" is recording an observation: it matters who saw it and when,
-- it accumulates over visits, and a later note can supersede an earlier one without erasing
-- that the earlier one was true at the time. A single editable field records none of that,
-- and the last person to type in it silently destroys whatever was there before.
--
-- WHY NOT JUST visit_note. A visit note is about one attendance ("couldn't reach the
-- filter, homeowner had boxes stacked"). A property note is standing knowledge about the
-- home that the NEXT technician needs before they arrive, whichever visit that is. Filing
-- standing knowledge under a single past visit buries it.
--
-- author_user_id is a bare BIGINT with no FK to users, matching visit_note's own comment:
-- deliberately no cross-domain hard FK. It is set from the authenticated principal, never
-- from a request body.
--
-- ─────────────────────────────────────────────────────────────────────────────
-- THIS IS NOT access_notes, AND THE DIFFERENCE MATTERS.
--
-- property.access_notes (V4) is BYTEA holding AES-256-GCM ciphertext, because it carries
-- how to get INTO the home: lockbox codes, alarm codes, key locations. It is encrypted at
-- the application layer, decrypted only server-side, and surfaced only to the assigned
-- technician on the day sheet.
--
-- These notes are plaintext and are shown wherever the property is shown. Anything that
-- would let a stranger enter the house belongs in access_notes, never here. The UI must say
-- so at the point of entry, because the failure mode is someone typing a lockbox code into
-- whichever box is in front of them.
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE property_note (
    id             BIGSERIAL    PRIMARY KEY,
    property_id    BIGINT       NOT NULL REFERENCES property (id) ON DELETE CASCADE,
    author_user_id BIGINT       NOT NULL,
    body           TEXT         NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- The only read pattern is "this property's notes, newest first", ordered and cursored on
-- id. For an append-only log insert order is log order, and id is the only key that is both
-- unique and monotonic, so it is the only one a keyset cursor can partition exactly without
-- risking a skipped row. See VisitNoteService's class javadoc.
CREATE INDEX idx_property_note_property ON property_note (property_id, id DESC);

COMMENT ON TABLE property_note IS
    'Plaintext technician notes about a property, newest-first. Never put access '
    'credentials here: lockbox/alarm codes and key locations belong in '
    'property.access_notes, which is encrypted.';
