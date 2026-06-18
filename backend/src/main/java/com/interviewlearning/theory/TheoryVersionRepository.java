package com.interviewlearning.theory;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Stores regenerated theory versions. Version 1 is always the on-disk
 * explanation (synthesized, not stored here); versions 2+ live in this table,
 * each tagged with the style it was generated in.
 */
@Repository
public class TheoryVersionRepository {

    /** One stored theory version. {@code createdAt} is an ISO-8601 string. */
    public record TheoryVersion(int versionNo, String style, String en, String ru, String createdAt) {
    }

    private final JdbcTemplate jdbc;

    public TheoryVersionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<TheoryVersion> list(String topicId) {
        return jdbc.query("""
                        SELECT version_no, style, en, ru, created_at
                        FROM theory_version WHERE topic_id = ? ORDER BY version_no
                        """,
                (rs, i) -> new TheoryVersion(
                        rs.getInt("version_no"),
                        rs.getString("style"),
                        rs.getString("en"),
                        rs.getString("ru"),
                        String.valueOf(rs.getObject("created_at"))),
                topicId);
    }

    /** Inserts a new version (numbered after the current max; first stored is 2). */
    public int add(String topicId, String style, String en, String ru) {
        Integer max = jdbc.queryForObject(
                "SELECT COALESCE(MAX(version_no), 1) FROM theory_version WHERE topic_id = ?",
                Integer.class, topicId);
        int next = (max == null ? 1 : max) + 1;
        jdbc.update(
                "INSERT INTO theory_version (topic_id, version_no, style, en, ru) VALUES (?, ?, ?, ?, ?)",
                topicId, next, style, en, ru);
        return next;
    }
}
