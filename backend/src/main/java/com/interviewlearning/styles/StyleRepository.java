package com.interviewlearning.styles;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Stores user-saved generation styles — custom "explain it with analogies from
 * THEME" instructions shown in the style dropdown. Built-in presets live on the
 * frontend; only custom ones are persisted here.
 */
@Repository
public class StyleRepository {

    /** A saved style: a display name + the instruction injected into generation. */
    public record Style(String name, String instruction) {
    }

    private final JdbcTemplate jdbc;

    public StyleRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Style> list() {
        return jdbc.query("SELECT name, instruction FROM styles ORDER BY name",
                (rs, i) -> new Style(rs.getString("name"), rs.getString("instruction")));
    }

    public void save(String name, String instruction) {
        jdbc.update("""
                INSERT INTO styles (name, instruction) VALUES (?, ?)
                ON CONFLICT (name) DO UPDATE SET instruction = EXCLUDED.instruction
                """, name, instruction);
    }

    public void delete(String name) {
        jdbc.update("DELETE FROM styles WHERE name = ?", name);
    }
}
