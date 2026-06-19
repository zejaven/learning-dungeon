package com.interviewlearning.questions;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.util.List;

/**
 * Catalog questions added by hand (no generation). They appear in the home tree
 * under the AI-chosen category and offer "generate theory" like any question.
 */
@Repository
public class QuestionRepository {

    /** {@code id} is the row id; the frontend uses {@code manual-<id>} as the entry id. */
    public record ManualQuestion(long id, String categoryId, String categoryName,
                                 int difficulty, String en, String ru) {
    }

    private final JdbcTemplate jdbc;

    public QuestionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<ManualQuestion> list() {
        return jdbc.query("""
                        SELECT id, category_id, category_name, difficulty, en, ru
                        FROM manual_question ORDER BY id
                        """,
                (rs, i) -> new ManualQuestion(
                        rs.getLong("id"),
                        rs.getString("category_id"),
                        rs.getString("category_name"),
                        rs.getInt("difficulty"),
                        rs.getString("en"),
                        rs.getString("ru")));
    }

    public ManualQuestion add(String categoryId, String categoryName, int difficulty, String en, String ru) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(con -> {
            // Return only the id column, so KeyHolder.getKey() isn't ambiguous.
            PreparedStatement ps = con.prepareStatement("""
                    INSERT INTO manual_question (category_id, category_name, difficulty, en, ru)
                    VALUES (?, ?, ?, ?, ?)
                    """, new String[] {"id"});
            ps.setString(1, categoryId);
            ps.setString(2, categoryName);
            ps.setInt(3, difficulty);
            ps.setString(4, en);
            ps.setString(5, ru);
            return ps;
        }, keys);
        long id = keys.getKey() == null ? 0 : keys.getKey().longValue();
        return new ManualQuestion(id, categoryId, categoryName, difficulty, en, ru);
    }

    public void delete(long id) {
        jdbc.update("DELETE FROM manual_question WHERE id = ?", id);
    }
}
