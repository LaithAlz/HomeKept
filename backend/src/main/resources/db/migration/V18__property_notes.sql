-- V18__property_notes.sql
-- Free-text operational notes about a property, editable by an admin.
--
-- WHY A PLAIN COLUMN, NOT A TABLE. Visit notes are a log: many entries, each by an author,
-- each about one visit, and `visit_note` (V7) already models that. Property notes are the
-- opposite shape: standing context a technician needs every single time they attend, like
-- "dog in the back yard", "gate sticks, lift while pushing", "ring twice, owner is hard of
-- hearing". That is one piece of current truth to edit, not a history to append to, so it
-- is a column on the property rather than a second note table.
--
-- ─────────────────────────────────────────────────────────────────────────────
-- THIS IS NOT access_notes, AND THE DIFFERENCE MATTERS.
--
-- property.access_notes (V4) is BYTEA holding AES-256-GCM ciphertext, because it carries
-- how to get INTO the home: lockbox codes, alarm codes, key locations. It is encrypted at
-- the application layer, decrypted only server-side, and surfaced only to the assigned
-- technician on the day sheet.
--
-- This column is plaintext and is shown wherever a property is shown. Anything that would
-- let a stranger enter the house belongs in access_notes, never here. The admin UI must say
-- so at the point of entry, because the failure mode is someone typing a lockbox code into
-- the convenient box instead of the encrypted one.
-- ─────────────────────────────────────────────────────────────────────────────
ALTER TABLE property
    ADD COLUMN notes TEXT;

COMMENT ON COLUMN property.notes IS
    'Plaintext operational notes shown with the property. Never put access credentials '
    'here: lockbox/alarm codes and key locations belong in access_notes, which is encrypted.';
