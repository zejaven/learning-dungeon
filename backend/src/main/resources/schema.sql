-- Progress persistence for the Java Interview Dungeon.
-- Run on every startup via spring.sql.init.mode=always; all idempotent.

-- Mission completion: one row per (topic, mission). Missions are binary, so no
-- history is kept here.
CREATE TABLE IF NOT EXISTS mission_progress (
    id           BIGSERIAL PRIMARY KEY,
    topic_id     TEXT        NOT NULL,
    mission_id   TEXT        NOT NULL,
    completed    BOOLEAN     NOT NULL DEFAULT FALSE,
    completed_at TIMESTAMPTZ,
    UNIQUE (topic_id, mission_id)
);

-- Boss-fight answers with full history. The "current" answer for a question is
-- the single row with deleted_at IS NULL. Re-answering soft-deletes the previous
-- current row (sets deleted_at) and inserts a new current row, so older answers,
-- verdicts and scores are preserved and visible only in the database.
CREATE TABLE IF NOT EXISTS boss_fight_answer (
    id             BIGSERIAL   PRIMARY KEY,
    topic_id       TEXT        NOT NULL,
    question_index INTEGER     NOT NULL,
    question_text  TEXT,
    answer         TEXT        NOT NULL,
    verdict        TEXT,
    score          INTEGER,
    passed         BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at     TIMESTAMPTZ
);

-- At most one live answer per question; superseded answers keep deleted_at set.
CREATE UNIQUE INDEX IF NOT EXISTS ux_boss_fight_current
    ON boss_fight_answer (topic_id, question_index)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS ix_boss_fight_history
    ON boss_fight_answer (topic_id, question_index, created_at);

-- Whole-topic completion: true once every boss-fight question has a passing
-- current answer.
CREATE TABLE IF NOT EXISTS topic_progress (
    topic_id     TEXT        PRIMARY KEY,
    completed    BOOLEAN     NOT NULL DEFAULT FALSE,
    completed_at TIMESTAMPTZ
);
