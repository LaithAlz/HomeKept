-- V1__identity.sql
-- Identity domain: users, refresh_tokens, password_reset_tokens
-- Conventions: BIGSERIAL PKs, TIMESTAMPTZ UTC, VARCHAR enums, all FKs indexed.

-- ─────────────────────────────────────────────────────────────────────────────
-- users
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE users (
    id             BIGSERIAL PRIMARY KEY,
    email          VARCHAR(255) NOT NULL,
    password_hash  TEXT         NOT NULL,
    first_name     VARCHAR(100) NOT NULL,
    last_name      VARCHAR(100) NOT NULL,
    phone          VARCHAR(30),
    role           VARCHAR(20)  NOT NULL CHECK (role IN ('CUSTOMER', 'TECHNICIAN', 'ADMIN')),
    status         VARCHAR(30)  NOT NULL CHECK (status IN ('ACTIVE', 'PENDING_ACTIVATION', 'SUSPENDED')),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    archived_at    TIMESTAMPTZ
);

-- Case-insensitive unique email index.
-- Using lower() so email lookups are always case-insensitive without requiring the
-- citext extension (avoids extension-install permission issues on managed Postgres).
CREATE UNIQUE INDEX idx_users_email_lower ON users (lower(email));

-- ─────────────────────────────────────────────────────────────────────────────
-- refresh_tokens
-- We store only the SHA-256 hash of the opaque token — never the raw token.
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE refresh_tokens (
    id          BIGSERIAL   PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  TEXT         NOT NULL,
    expires_at  TIMESTAMPTZ  NOT NULL,
    revoked_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_refresh_tokens_user_id   ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_hash      ON refresh_tokens (token_hash);

-- ─────────────────────────────────────────────────────────────────────────────
-- password_reset_tokens (table only — no endpoints in this slice)
-- Endpoints wired in the notification slice when SendGrid is available.
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE password_reset_tokens (
    id           BIGSERIAL   PRIMARY KEY,
    user_id      BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash   TEXT         NOT NULL,
    expires_at   TIMESTAMPTZ  NOT NULL,
    consumed_at  TIMESTAMPTZ,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_prt_user_id    ON password_reset_tokens (user_id);
CREATE INDEX idx_prt_token_hash ON password_reset_tokens (token_hash);

