-- V13__password_reset_token_purpose.sql
-- Separates the two kinds of token that share password_reset_tokens.
--
-- Staff invites reuse this table (it is already user-bound, HMAC signed, stored
-- hash-only and single-use at the database level) but with a seven-day lifetime
-- rather than the customer reset flow's thirty minutes. Without a purpose the two
-- are indistinguishable, so a long-lived staff invite could be redeemed at
-- POST /api/auth/reset to set a password and open a session, and invalidating a
-- user's invites would also destroy a password reset they had just requested.
--
-- Every existing row predates staff invites, so they are all password resets.
ALTER TABLE password_reset_tokens
    ADD COLUMN purpose VARCHAR(20) NOT NULL DEFAULT 'PASSWORD_RESET';

ALTER TABLE password_reset_tokens
    ADD CONSTRAINT password_reset_tokens_purpose_check
    CHECK (purpose IN ('PASSWORD_RESET', 'STAFF_INVITE'));

-- Lookups are always scoped to one user and one purpose.
CREATE INDEX idx_prt_user_purpose ON password_reset_tokens (user_id, purpose);
