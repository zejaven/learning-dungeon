package com.interviewlearning.sql;

import com.interviewlearning.sql.SqlDtos.SqlResult;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlServiceTest {

    private final SqlService sql = new SqlService();

    private static final String SCHEMA = """
            CREATE TABLE courses (id INT PRIMARY KEY, name VARCHAR(50));
            CREATE TABLE enrollments (employee_id INT, course_id INT);
            INSERT INTO courses VALUES (1, 'Java');
            INSERT INTO courses VALUES (2, 'SQL');
            INSERT INTO enrollments VALUES (10, 1);
            INSERT INTO enrollments VALUES (11, 1);
            INSERT INTO enrollments VALUES (12, 1);
            INSERT INTO enrollments VALUES (13, 2);
            """;

    private static final String EXPECTED =
            "SELECT c.id, c.name FROM courses c JOIN enrollments e ON e.course_id = c.id "
                    + "GROUP BY c.id, c.name HAVING COUNT(*) > 2";

    @Test
    void runReturnsResultRows() {
        SqlResult r = sql.run(SCHEMA, "SELECT id, name FROM courses ORDER BY id");
        assertNull(r.error());
        assertEquals(2, r.rows().size());
        assertTrue(r.rows().stream().anyMatch(row -> row.contains("Java")));
    }

    @Test
    void anEquivalentQueryMatchesTheExpected() {
        // Different alias and an explicit count column, but the same result rows.
        String userSql = "SELECT c.id, c.name FROM courses c JOIN enrollments en ON en.course_id = c.id "
                + "GROUP BY c.id, c.name HAVING COUNT(en.employee_id) > 2";
        assertTrue(sql.matches(SCHEMA, userSql, EXPECTED, false));
    }

    @Test
    void aWrongQueryDoesNotMatch() {
        // Returns both courses, not just the one with > 2 enrollments.
        assertFalse(sql.matches(SCHEMA, "SELECT id, name FROM courses", EXPECTED, false));
    }

    @Test
    void aSyntaxErrorIsReported() {
        SqlResult r = sql.run(SCHEMA, "SELEC * FROM courses");
        assertNotNull(r.error());
        assertTrue(r.rows().isEmpty());
    }

    @Test
    void showcaseTopicSeedAndExpectedQueriesWork() throws IOException {
        String schema = Files.readString(
                topicsDir().resolve("sql-many-to-many/starter/schema.sql"), StandardCharsets.UTF_8);

        SqlResult perCourse = sql.run(schema,
                "SELECT c.name, COUNT(*) FROM courses c JOIN enrollments e ON e.course_id = c.id GROUP BY c.name");
        assertNull(perCourse.error());
        assertEquals(4, perCourse.rows().size(), "four courses");

        SqlResult overTen = sql.run(schema,
                "SELECT c.id, c.name FROM courses c JOIN enrollments e ON e.course_id = c.id "
                        + "GROUP BY c.id, c.name HAVING COUNT(*) > 10");
        assertNull(overTen.error());
        assertEquals(2, overTen.rows().size(), "Java (12) and SQL (11) have > 10 enrolments");
    }

    private static Path topicsDir() {
        Path p = Paths.get("").toAbsolutePath();
        while (p != null) {
            Path t = p.resolve("topics");
            if (Files.isDirectory(t)) return t;
            p = p.getParent();
        }
        throw new IllegalStateException("topics/ not found");
    }
}
