package com.interviewlearning.theory;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Stores regenerated theory versions. Version 1 is always the on-disk
 * explanation (synthesized, not stored here); versions 2+ live in this table,
 * each tagged with the style and AI provider it was generated with.
 */
@Repository
public class TheoryVersionRepository {

    /** One stored theory version. {@code createdAt} is an ISO-8601 string. */
    public record TheoryVersion(int versionNo, String style, String en, String ru, String createdAt,
                                String aiProvider, String aiModel) {
    }

    private final JdbcTemplate jdbc;

    public TheoryVersionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<TheoryVersion> list(String topicId) {
        return jdbc.query("""
                        SELECT version_no, style, en, ru, created_at, ai_provider, ai_model
                        FROM theory_version WHERE topic_id = ? ORDER BY version_no
                        """,
                (rs, i) -> new TheoryVersion(
                        rs.getInt("version_no"),
                        rs.getString("style"),
                        rs.getString("en"),
                        rs.getString("ru"),
                        String.valueOf(rs.getObject("created_at")),
                        rs.getString("ai_provider"),
                        rs.getString("ai_model")),
                topicId);
    }

    /** Inserts a new version (numbered after the current max; first stored is 2). */
    public int add(String topicId, String style, String en, String ru, String aiProvider, String aiModel) {
        Integer max = jdbc.queryForObject(
                "SELECT COALESCE(MAX(version_no), 1) FROM theory_version WHERE topic_id = ?",
                Integer.class, topicId);
        int next = (max == null ? 1 : max) + 1;
        jdbc.update(
                """
                        INSERT INTO theory_version
                            (topic_id, version_no, style, en, ru, ai_provider, ai_model)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """,
                topicId, next, style, en, ru,
                aiProvider == null || aiProvider.isBlank() ? "claude" : aiProvider,
                aiModel == null ? "" : aiModel);
        return next;
    }
}
