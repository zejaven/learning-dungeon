package com.interviewlearning.questions;

import com.interviewlearning.topics.TopicDtos.Localized;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Catalog questions added by hand (no generation). They appear in the home tree
 * under the AI-chosen category and offer "generate theory" like any question.
 *
 * <p>The question text is stored one row per language in
 * {@code manual_question_text}, so a question carries whatever languages the
 * classifier produced rather than a fixed pair.
 */
@Repository
public class QuestionRepository {

    /** {@code id} is the row id; the frontend uses {@code manual-<id>} as the entry id. */
    public record ManualQuestion(long id, String categoryId, String categoryName,
                                 int difficulty, Localized question) {
    }

    private final JdbcTemplate jdbc;

    public QuestionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<ManualQuestion> list() {
        Map<Long, Row> byId = new LinkedHashMap<>();
        jdbc.query("""
                        SELECT q.id, q.category_id, q.category_name, q.difficulty, t.lang, t.text
                        FROM manual_question q
                        LEFT JOIN manual_question_text t ON t.question_id = q.id
                        ORDER BY q.id, t.lang
                        """,
                rs -> {
                    long id = rs.getLong("id");
                    Row row = byId.get(id);
                    if (row == null) {
                        row = new Row(id, rs.getString("category_id"), rs.getString("category_name"),
                                rs.getInt("difficulty"));
                        byId.put(id, row);
                    }
                    String lang = rs.getString("lang");
                    if (lang != null) {
                        row.texts.put(lang, rs.getString("text"));
                    }
                });
        List<ManualQuestion> result = new ArrayList<>();
        for (Row row : byId.values()) {
            result.add(new ManualQuestion(row.id, row.categoryId, row.categoryName, row.difficulty,
                    new Localized(row.texts)));
        }
        return result;
    }

    @Transactional
    public ManualQuestion add(String categoryId, String categoryName, int difficulty,
                              Map<String, String> texts) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(con -> {
            // Return only the id column, so KeyHolder.getKey() isn't ambiguous.
            PreparedStatement ps = con.prepareStatement("""
                    INSERT INTO manual_question (category_id, category_name, difficulty)
                    VALUES (?, ?, ?)
                    """, new String[] {"id"});
            ps.setString(1, categoryId);
            ps.setString(2, categoryName);
            ps.setInt(3, difficulty);
            return ps;
        }, keys);
        long id = keys.getKey() == null ? 0 : keys.getKey().longValue();
        for (Map.Entry<String, String> e : texts.entrySet()) {
            if (e.getValue() == null || e.getValue().isBlank()) {
                continue;
            }
            jdbc.update("""
                            INSERT INTO manual_question_text (question_id, lang, text)
                            VALUES (?, ?, ?)
                            ON CONFLICT (question_id, lang) DO UPDATE SET text = EXCLUDED.text
                            """,
                    id, e.getKey(), e.getValue());
        }
        return new ManualQuestion(id, categoryId, categoryName, difficulty, new Localized(texts));
    }

    public void delete(long id) {
        jdbc.update("DELETE FROM manual_question WHERE id = ?", id);
    }

    /** Mutable accumulator while folding the joined rows of one question. */
    private static final class Row {
        final long id;
        final String categoryId;
        final String categoryName;
        final int difficulty;
        final Map<String, String> texts = new LinkedHashMap<>();

        Row(long id, String categoryId, String categoryName, int difficulty) {
            this.id = id;
            this.categoryId = categoryId;
            this.categoryName = categoryName;
            this.difficulty = difficulty;
        }
    }
}
