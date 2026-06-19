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
                    en         TEXT        NOT NULL,
                    ru         TEXT        NOT NULL,
                    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                    UNIQUE (topic_id, version_no)
                )
                """);
    }
}
