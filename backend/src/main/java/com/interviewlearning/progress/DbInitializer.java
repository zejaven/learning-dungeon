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
                    en            TEXT        NOT NULL,
                    ru            TEXT        NOT NULL,
                    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
                )
                """);

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
                    en         TEXT        NOT NULL,
                    ru         TEXT        NOT NULL,
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

        // --- "Learn by micro-actions" lesson mode --------------------------

        // Append-only log of every answered micro-exercise (lesson and review
        // contexts). atoms_hash records which generation of learning-atoms.json
        // the answer belonged to.
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS lesson_exercise_answer (
                    id          BIGSERIAL   PRIMARY KEY,
                    topic_id    TEXT        NOT NULL,
                    exercise_id TEXT        NOT NULL,
                    atom_id     TEXT,
                    unit_id     TEXT,
                    context     TEXT        NOT NULL DEFAULT 'lesson',
                    atoms_hash  TEXT        NOT NULL DEFAULT '',
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

        // One row per completed lesson unit per atoms generation: regenerating
        // learning-atoms.json changes the hash and thereby resets progress
        // without deleting history.
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS lesson_unit_progress (
                    id           BIGSERIAL   PRIMARY KEY,
                    topic_id     TEXT        NOT NULL,
                    unit_id      TEXT        NOT NULL,
                    atoms_hash   TEXT        NOT NULL,
                    completed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                    UNIQUE (topic_id, unit_id, atoms_hash)
                )
                """);

        // Lesson-level completion; distinct from topic_progress (which stays
        // boss-fight-driven). Scoped by atoms_hash for the same reset semantics.
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS lesson_progress (
                    topic_id     TEXT        PRIMARY KEY,
                    atoms_hash   TEXT        NOT NULL,
                    completed    BOOLEAN     NOT NULL DEFAULT FALSE,
                    completed_at TIMESTAMPTZ
                )
                """);

        // Explicit membership of the global review pool: a topic's practice
        // exercises join when its lesson is fully completed. Stale rows (the
        // exercise no longer exists after a regeneration) are pruned lazily.
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS review_pool (
                    id               BIGSERIAL   PRIMARY KEY,
                    topic_id         TEXT        NOT NULL,
                    exercise_id      TEXT        NOT NULL,
                    atom_id          TEXT,
                    atoms_hash       TEXT        NOT NULL,
                    added_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
                    last_reviewed_at TIMESTAMPTZ,
                    last_correct     BOOLEAN,
                    correct_count    INT         NOT NULL DEFAULT 0,
                    wrong_count      INT         NOT NULL DEFAULT 0,
                    UNIQUE (topic_id, exercise_id)
                )
                """);

        // A review run over the pool; state_json holds the shuffled item list,
        // the requeue queue and the cursor, so a reload resumes mid-session.
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS review_session (
                    id          BIGSERIAL   PRIMARY KEY,
                    started_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
                    finished_at TIMESTAMPTZ,
                    state_json  TEXT        NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE UNIQUE INDEX IF NOT EXISTS ux_review_session_active
                    ON review_session ((1)) WHERE finished_at IS NULL
                """);
    }
}
