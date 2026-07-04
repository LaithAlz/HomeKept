-- V9__health_score_snapshot.sql
-- Home Health Score v1 (#53). The score is computed on read (weighted checklist outcomes +
-- open flags); a snapshot is written per completed visit so the dashboard delta has a prior
-- value to compare against.
-- Conventions: BIGSERIAL PK, TIMESTAMPTZ UTC, INTEGER for small bounded ints (matches
-- duration_minutes / materials_cost_cents), CHECK for the 0..100 range.

-- ─────────────────────────────────────────────────────────────────────────────
-- health_score_snapshot
-- ─────────────────────────────────────────────────────────────────────────────
-- subscriber_id: CASCADE — snapshots are disposable derived history; losing them with a
--                deleted subscriber is fine (unlike flag/todo, which are RESTRICT).
-- score: 0..100, computed at the moment a visit was completed.
-- Composite index (subscriber_id, computed_at) so "latest snapshot for this subscriber"
-- (the delta lookup) is a direct seek, not a fetch-then-sort. No DESC — Postgres scans the
-- B-tree backward just as cheaply for our single-column ORDER BY.

CREATE TABLE health_score_snapshot (
    id            BIGSERIAL    PRIMARY KEY,
    subscriber_id BIGINT       NOT NULL REFERENCES subscriber (id) ON DELETE CASCADE,
    score         INTEGER      NOT NULL CHECK (score BETWEEN 0 AND 100),
    computed_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_health_score_snapshot_subscriber
    ON health_score_snapshot (subscriber_id, computed_at);
