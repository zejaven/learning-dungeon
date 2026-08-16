package com.interviewlearning.theory;

import com.interviewlearning.topics.TopicDtos.Localized;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Stores regenerated theory versions. Version 1 is always the on-disk
 * explanation (synthesized, not stored here); versions 2+ live in this table,
 * each tagged with the style and AI provider it was generated with.
 *
 * <p>A version's text is stored one row per language in {@code
 * theory_version_text}, so a version may carry any subset of languages and a
 * missing translation can be added to it later.
 */
@Repository
public class TheoryVersionRepository {

    /** One stored theory version. {@code createdAt} is an ISO-8601 string. */
    public record TheoryVersion(long id, int versionNo, String style, Localized texts,
                                String createdAt, String aiProvider, String aiModel) {
    }

    private final JdbcTemplate jdbc;

    public TheoryVersionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** All versions of a topic with their per-language texts, oldest first. */
    public List<TheoryVersion> list(String topicId) {
        // One query: rows are ordered by version so the fold below stays linear.
        Map<Integer, Row> byVersion = new LinkedHashMap<>();
        jdbc.query("""
                        SELECT v.id, v.version_no, v.style, v.created_at, v.ai_provider, v.ai_model,
                               t.lang, t.text
                        FROM theory_version v
                        LEFT JOIN theory_version_text t ON t.version_id = v.id
                        WHERE v.topic_id = ?
                        ORDER BY v.version_no, t.lang
                        """,
                rs -> {
                    int versionNo = rs.getInt("version_no");
                    Row row = byVersion.get(versionNo);
                    if (row == null) {
                        row = new Row(rs.getLong("id"), versionNo, rs.getString("style"),
                                str(rs.getObject("created_at")), rs.getString("ai_provider"),
                                rs.getString("ai_model"));
                        byVersion.put(versionNo, row);
                    }
                    String lang = rs.getString("lang");
                    if (lang != null) {
                        row.texts.put(lang, rs.getString("text"));
                    }
                },
                topicId);

        List<TheoryVersion> result = new ArrayList<>();
        for (Row row : byVersion.values()) {
            result.add(new TheoryVersion(row.id, row.versionNo, row.style, new Localized(row.texts),
                    row.createdAt, row.aiProvider, row.aiModel));
        }
        return result;
    }

    /** Inserts a new version (numbered after the current max; first stored is 2). */
    @Transactional
    public int add(String topicId, String style, Map<String, String> texts,
                   String aiProvider, String aiModel) {
        Integer max = jdbc.queryForObject(
                "SELECT COALESCE(MAX(version_no), 1) FROM theory_version WHERE topic_id = ?",
                Integer.class, topicId);
        int next = (max == null ? 1 : max) + 1;
        Long id = jdbc.queryForObject(
                """
                        INSERT INTO theory_version
                            (topic_id, version_no, style, ai_provider, ai_model)
                        VALUES (?, ?, ?, ?, ?)
                        RETURNING id
                        """,
                Long.class,
                topicId, next, style,
                aiProvider == null || aiProvider.isBlank() ? "claude" : aiProvider,
                aiModel == null ? "" : aiModel);
        writeTexts(id, texts);
        return next;
    }

    /** Adds or replaces the text of some languages of an existing version. */
    @Transactional
    public void addTexts(String topicId, int versionNo, Map<String, String> texts) {
        Long id = idOf(topicId, versionNo).orElseThrow(
                () -> new IllegalArgumentException("No version " + versionNo + " of topic " + topicId));
        writeTexts(id, texts);
    }

    public Optional<Long> idOf(String topicId, int versionNo) {
        List<Long> ids = jdbc.queryForList(
                "SELECT id FROM theory_version WHERE topic_id = ? AND version_no = ?",
                Long.class, topicId, versionNo);
        return ids.isEmpty() ? Optional.empty() : Optional.of(ids.get(0));
    }

    private void writeTexts(Long versionId, Map<String, String> texts) {
        if (versionId == null || texts == null) {
            return;
        }
        for (Map.Entry<String, String> e : texts.entrySet()) {
            if (e.getValue() == null || e.getValue().isBlank()) {
                continue;
            }
            jdbc.update("""
                            INSERT INTO theory_version_text (version_id, lang, text)
                            VALUES (?, ?, ?)
                            ON CONFLICT (version_id, lang) DO UPDATE SET text = EXCLUDED.text
                            """,
                    versionId, e.getKey(), e.getValue());
        }
    }

    /** Mutable accumulator while folding the joined rows of one version. */
    private static final class Row {
        final long id;
        final int versionNo;
        final String style;
        final String createdAt;
        final String aiProvider;
        final String aiModel;
        final Map<String, String> texts = new LinkedHashMap<>();

        Row(long id, int versionNo, String style, String createdAt, String aiProvider, String aiModel) {
            this.id = id;
            this.versionNo = versionNo;
            this.style = style;
            this.createdAt = createdAt;
            this.aiProvider = aiProvider;
            this.aiModel = aiModel;
        }
    }

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
