package com.interviewlearning.progress;

import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Creates and migrates the progress tables on startup. Run from Java (not
 * spring.sql.init) because the schema contains a PL/pgSQL {@code DO $$ ... $$}
 * migration block: Spring's script splitter breaks on the {@code ;} inside the
 * block, whereas the PostgreSQL JDBC driver parses dollar quoting correctly when
 * each statement is executed intact. Statements are ordered so the column
 * migration happens before the indexes that reference the new column. All
 * statements are idempotent.
 */
@Component
public class DbInitializer {

    private final JdbcTemplate jdbc;

    public DbInitializer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    void init() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS mission_progress (
                    id           BIGSERIAL PRIMARY KEY,
                    topic_id     TEXT        NOT NULL,
                    mission_id   TEXT        NOT NULL,
                    completed    BOOLEAN     NOT NULL DEFAULT FALSE,
                    completed_at TIMESTAMPTZ,
                    UNIQUE (topic_id, mission_id)
                )
                """);

        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS boss_fight_answer (
                    id             BIGSERIAL   PRIMARY KEY,
                    topic_id       TEXT        NOT NULL,
                    question_id    TEXT        NOT NULL,
                    question_text  TEXT,
                    answer         TEXT        NOT NULL,
                    verdict        TEXT,
                    score          INTEGER,
                    passed         BOOLEAN     NOT NULL DEFAULT FALSE,
                    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
                    deleted_at     TIMESTAMPTZ
                )
                """);

        // Migrate tables created before boss-fight questions had stable ids:
        // rename the old positional question_index column to a textual
        // question_id. Idempotent — skips if already migrated.
        jdbc.execute("""
                DO $$
                BEGIN
                    IF EXISTS (
                        SELECT 1 FROM information_schema.columns
                        WHERE table_name = 'boss_fight_answer'
                          AND column_name = 'question_index'
                    ) THEN
                        ALTER TABLE boss_fight_answer RENAME COLUMN question_index TO question_id;
                        ALTER TABLE boss_fight_answer
                            ALTER COLUMN question_id TYPE TEXT USING question_id::text;
                    END IF;
                END $$
                """);

        // At most one live answer per question; superseded answers keep
        // deleted_at set so history is retained.
        jdbc.execute("""
                CREATE UNIQUE INDEX IF NOT EXISTS ux_boss_fight_current
                    ON boss_fight_answer (topic_id, question_id)
                    WHERE deleted_at IS NULL
                """);
        jdbc.execute("""
                CREATE INDEX IF NOT EXISTS ix_boss_fight_history
                    ON boss_fight_answer (topic_id, question_id, created_at)
                """);

        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS topic_progress (
                    topic_id     TEXT        PRIMARY KEY,
                    completed    BOOLEAN     NOT NULL DEFAULT FALSE,
                    completed_at TIMESTAMPTZ
                )
                """);

        // Free-form Ask-AI questions, kept as an append-only per-topic log so the
        // learner can revisit past questions and their answers. Unlike boss-fight
        // answers there is no fixed question set and no versioning — each ask is a
        // distinct entry; deletion removes the row outright.
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS assistant_question (
                    id         BIGSERIAL   PRIMARY KEY,
                    topic_id   TEXT        NOT NULL,
                    question   TEXT        NOT NULL,
                    answer     TEXT        NOT NULL,
                    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
                )
                """);
        jdbc.execute("""
                CREATE INDEX IF NOT EXISTS ix_assistant_question
                    ON assistant_question (topic_id, created_at, id)
                """);

        // User-saved generation styles (custom analogy themes for explanations).
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS styles (
                    name        TEXT        PRIMARY KEY,
                    instruction TEXT        NOT NULL,
                    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
                )
                """);

        // Questions added to the catalog by hand (category chosen by the AI).
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS manual_question (
                    id            BIGSERIAL   PRIMARY KEY,
                    category_id   TEXT        NOT NULL,
                    category_name TEXT,
                    difficulty    INT         NOT NULL DEFAULT 2,
                    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
                )
                """);

        // The question text, one row per language (same shape as theory versions).
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS manual_question_text (
                    question_id BIGINT      NOT NULL REFERENCES manual_question(id) ON DELETE CASCADE,
                    lang        TEXT        NOT NULL,
                    text        TEXT        NOT NULL,
                    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
                    PRIMARY KEY (question_id, lang)
                )
                """);

        // Migration from the fixed en/ru columns, guarded by their existence so
        // it is a no-op on a fresh database and on every later start.
        jdbc.execute("""
                DO $$
                BEGIN
                    IF EXISTS (SELECT 1 FROM information_schema.columns
                               WHERE table_name = 'manual_question' AND column_name = 'en') THEN
                        INSERT INTO manual_question_text (question_id, lang, text)
                            SELECT id, 'en', en FROM manual_question
                            WHERE en IS NOT NULL AND en <> ''
                            ON CONFLICT (question_id, lang) DO NOTHING;
                        INSERT INTO manual_question_text (question_id, lang, text)
                            SELECT id, 'ru', ru FROM manual_question
                            WHERE ru IS NOT NULL AND ru <> ''
                            ON CONFLICT (question_id, lang) DO NOTHING;
                    END IF;
                END $$
                """);
        jdbc.execute("ALTER TABLE manual_question DROP COLUMN IF EXISTS en");
        jdbc.execute("ALTER TABLE manual_question DROP COLUMN IF EXISTS ru");

        // Regenerated theory versions: version 1 is the on-disk explanation;
        // versions 2+ are stored here, each tagged with the style used.
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS theory_version (
                    id         BIGSERIAL   PRIMARY KEY,
                    topic_id   TEXT        NOT NULL,
                    version_no INT         NOT NULL,
                    style      TEXT        NOT NULL DEFAULT 'Default',
                    ai_provider TEXT       NOT NULL DEFAULT 'claude',
                    ai_model    TEXT       NOT NULL DEFAULT '',
                    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                    UNIQUE (topic_id, version_no)
                )
                """);

        jdbc.execute("""
                ALTER TABLE theory_version
                    ADD COLUMN IF NOT EXISTS ai_provider TEXT NOT NULL DEFAULT 'claude'
                """);
        jdbc.execute("""
                ALTER TABLE theory_version
                    ADD COLUMN IF NOT EXISTS ai_model TEXT NOT NULL DEFAULT ''
                """);

        // A version's text, one row per language, so a version can carry any
        // subset of languages and gain a translation later.
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS theory_version_text (
                    version_id BIGINT      NOT NULL REFERENCES theory_version(id) ON DELETE CASCADE,
                    lang       TEXT        NOT NULL,
                    text       TEXT        NOT NULL,
                    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                    PRIMARY KEY (version_id, lang)
                )
                """);

        // Migration from the fixed en/ru columns. Guarded by the columns still
        // existing, so it is a no-op on a fresh database and on later starts.
        jdbc.execute("""
                DO $$
                BEGIN
                    IF EXISTS (SELECT 1 FROM information_schema.columns
                               WHERE table_name = 'theory_version' AND column_name = 'en') THEN
                        INSERT INTO theory_version_text (version_id, lang, text)
                            SELECT id, 'en', en FROM theory_version
                            WHERE en IS NOT NULL AND en <> ''
                            ON CONFLICT (version_id, lang) DO NOTHING;
                        INSERT INTO theory_version_text (version_id, lang, text)
                            SELECT id, 'ru', ru FROM theory_version
                            WHERE ru IS NOT NULL AND ru <> ''
                            ON CONFLICT (version_id, lang) DO NOTHING;
                    END IF;
                END $$
                """);
        jdbc.execute("ALTER TABLE theory_version DROP COLUMN IF EXISTS en");
        jdbc.execute("ALTER TABLE theory_version DROP COLUMN IF EXISTS ru");

        // --- "Learn by micro-actions" lesson mode --------------------------

        // One row per answered micro-exercise (lesson and review contexts),
        // keyed by the stable exercise id — progress is not tied to the file.
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS lesson_exercise_answer (
                    id          BIGSERIAL   PRIMARY KEY,
                    topic_id    TEXT        NOT NULL,
                    exercise_id TEXT        NOT NULL,
                    atom_id     TEXT,
                    unit_id     TEXT,
                    context     TEXT        NOT NULL DEFAULT 'lesson',
                    answer_json TEXT        NOT NULL,
                    correct     BOOLEAN     NOT NULL,
                    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
                )
                """);
        jdbc.execute("""
                CREATE INDEX IF NOT EXISTS ix_lesson_answer
                    ON lesson_exercise_answer (topic_id, exercise_id, created_at)
                """);
        // Keep at most one answer per (topic, exercise, context): re-answering
        // updates the row instead of piling up history. De-duplicate any rows
        // written before this index existed (keep the latest by id), then
        // enforce it.
        jdbc.execute("""
                DELETE FROM lesson_exercise_answer a
                USING lesson_exercise_answer b
                WHERE a.id < b.id
                  AND a.topic_id = b.topic_id
                  AND a.exercise_id = b.exercise_id
                  AND a.context = b.context
                """);
        jdbc.execute("""
                CREATE UNIQUE INDEX IF NOT EXISTS ux_lesson_answer_current
                    ON lesson_exercise_answer (topic_id, exercise_id, context)
                """);

        // Lesson-level completion; distinct from topic_progress (which stays
        // boss-fight-driven). Derived from answers, keyed by topic.
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS lesson_progress (
                    topic_id     TEXT        PRIMARY KEY,
                    completed    BOOLEAN     NOT NULL DEFAULT FALSE,
                    completed_at TIMESTAMPTZ
                )
                """);

        // Explicit membership of the global review pool: a practice exercise
        // joins the first time it is answered in a lesson (ReviewEnrollment),
        // not when the whole lesson is completed. Stale rows (the
        // exercise no longer exists after an edit) are pruned lazily.
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS review_pool (
                    id               BIGSERIAL   PRIMARY KEY,
                    topic_id         TEXT        NOT NULL,
                    exercise_id      TEXT        NOT NULL,
                    atom_id          TEXT,
                    added_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
                    last_reviewed_at TIMESTAMPTZ,
                    last_correct     BOOLEAN,
                    correct_count    INT         NOT NULL DEFAULT 0,
                    wrong_count      INT         NOT NULL DEFAULT 0,
                    UNIQUE (topic_id, exercise_id)
                )
                """);

        // Superseded by the spaced-repetition columns below, but kept for one
        // release as a read-only witness: the migration that replaces it cannot
        // be tested (this project has no database tests) and it is the only
        // record of what had already been answered.
        jdbc.execute("ALTER TABLE review_pool ADD COLUMN IF NOT EXISTS pending BOOLEAN NOT NULL DEFAULT TRUE");

        // Spaced repetition. A pooled exercise walks a ladder of intervals
        // (see ReviewSchedule): srs_step is the rung it is about to be served
        // at, due_at is when that happens, and it stays answerable for a window
        // after due_at — missing that window is a lapse and restarts the ladder.
        //
        // expires_at is deliberately NOT stored: it is a pure function of due_at
        // and srs_step, and the ladder will be tuned — a stored copy would drift
        // out of agreement with the code. last_answer_id makes an answer
        // idempotent: the offline outbox aborts a request after 8s and keeps it
        // queued even though the server may have processed it, and replaying an
        // answer would climb the ladder twice.
        jdbc.execute("ALTER TABLE review_pool ADD COLUMN IF NOT EXISTS srs_step INT NOT NULL DEFAULT 0");
        jdbc.execute("ALTER TABLE review_pool ADD COLUMN IF NOT EXISTS due_at TIMESTAMPTZ NOT NULL DEFAULT now()");
        jdbc.execute("ALTER TABLE review_pool ADD COLUMN IF NOT EXISTS graduated_at TIMESTAMPTZ");
        jdbc.execute("ALTER TABLE review_pool ADD COLUMN IF NOT EXISTS lapse_count INT NOT NULL DEFAULT 0");
        jdbc.execute("ALTER TABLE review_pool ADD COLUMN IF NOT EXISTS last_answer_id TEXT");

        // One-shot backfill from the old boolean, guarded by a marker column
        // because there is no schema versioning here and this must not re-run:
        // a second pass would shove every row's due date forward again.
        // Exercises answered before the switch are credited with the first rung
        // and staggered by the second interval, so the upgrade does not greet
        // the user with a wall of several hundred due questions.
        jdbc.execute("""
                DO $$
                BEGIN
                    IF NOT EXISTS (
                        SELECT 1 FROM information_schema.columns
                        WHERE table_name = 'review_pool'
                          AND column_name = 'srs_migrated'
                    ) THEN
                        ALTER TABLE review_pool ADD COLUMN srs_migrated BOOLEAN NOT NULL DEFAULT TRUE;
                        UPDATE review_pool
                           SET srs_step = 1, due_at = now() + INTERVAL '8 hours'
                         WHERE pending = FALSE;
                        UPDATE review_pool
                           SET srs_step = 0, due_at = now()
                         WHERE pending = TRUE;
                    END IF;
                END $$
                """);

        // The pool is read whole and filtered by date in Java (so the interval
        // ladder stays in one testable place), which leaves this index only one
        // job: keep the graduated rows cheap to skip as they pile up.
        jdbc.execute("""
                CREATE INDEX IF NOT EXISTS ix_review_pool_live
                    ON review_pool (topic_id, exercise_id) WHERE graduated_at IS NULL
                """);

        // Migration: progress is now keyed by stable exercise ids, so the
        // atoms_hash columns (and the hash-scoped lesson_unit_progress table)
        // are gone. Drop them from databases created before this change.
        jdbc.execute("DROP TABLE IF EXISTS lesson_unit_progress");
        jdbc.execute("ALTER TABLE lesson_exercise_answer DROP COLUMN IF EXISTS atoms_hash");
        jdbc.execute("ALTER TABLE lesson_progress DROP COLUMN IF EXISTS atoms_hash");
        jdbc.execute("ALTER TABLE review_pool DROP COLUMN IF EXISTS atoms_hash");

        // Per-topic review preference: whether a topic's pooled exercises take
        // part in review sessions. Absence of a row means enabled (the default),
        // so a topic joins review automatically once its lesson is completed.
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS review_topic_pref (
                    topic_id TEXT    PRIMARY KEY,
                    enabled  BOOLEAN NOT NULL DEFAULT TRUE
                )
                """);

        // The review list is now per-exercise schedule state on review_pool
        // rather than a shuffled session snapshot. Drop the obsolete session table.
        jdbc.execute("DROP TABLE IF EXISTS review_session");
    }
}
