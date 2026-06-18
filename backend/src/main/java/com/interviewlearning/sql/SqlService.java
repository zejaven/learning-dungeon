package com.interviewlearning.sql;

import com.interviewlearning.sql.SqlDtos.SqlResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Runs a SQL topic's queries against a disposable in-memory H2 database
 * (PostgreSQL compatibility mode). Each call spins up a fresh database, seeds it
 * from the topic's schema, runs the query, and throws the database away — so user
 * SQL is fully isolated from the app's real Postgres and from other requests.
 */
@Service
public class SqlService {

    private static final Logger log = LoggerFactory.getLogger(SqlService.class);
    private static final int MAX_ROWS = 500;

    static {
        try {
            Class.forName("org.h2.Driver");
        } catch (ClassNotFoundException e) {
            // h2 is a compile dependency; this should never happen.
        }
    }

    /** Seeds a fresh DB and runs {@code userSql}, returning the result table or an error. */
    public SqlResult run(String schemaSql, String userSql) {
        try (Connection c = open()) {
            seed(c, schemaSql);
            return exec(c, userSql);
        } catch (SQLException e) {
            return SqlResult.error(firstLine(e.getMessage()));
        }
    }

    /**
     * True when {@code userSql} produces the same result as {@code expectedSql}
     * against the same seed. Columns are compared by count + position; rows as a
     * multiset unless {@code ordered}.
     */
    public boolean matches(String schemaSql, String userSql, String expectedSql, boolean ordered) {
        SqlResult user = run(schemaSql, userSql);
        if (user.error() != null) {
            return false;
        }
        SqlResult expected = run(schemaSql, expectedSql);
        if (expected.error() != null) {
            log.warn("SQL mission expectedSql failed: {}", expected.error());
            return false;
        }
        return sameTable(user, expected, ordered);
    }

    private Connection open() throws SQLException {
        String name = "t_" + UUID.randomUUID().toString().replace("-", "");
        return DriverManager.getConnection("jdbc:h2:mem:" + name + ";MODE=PostgreSQL;DB_CLOSE_DELAY=0");
    }

    private void seed(Connection c, String schemaSql) throws SQLException {
        if (schemaSql == null) {
            return;
        }
        // The seed is author-controlled; a naive split on ';' is enough.
        for (String raw : schemaSql.split(";")) {
            String stmt = raw.trim();
            if (stmt.isEmpty()) {
                continue;
            }
            try (Statement st = c.createStatement()) {
                st.execute(stmt);
            }
        }
    }

    private SqlResult exec(Connection c, String sql) {
        try (Statement st = c.createStatement()) {
            boolean hasResultSet = st.execute(sql);
            if (!hasResultSet) {
                int n = st.getUpdateCount();
                return new SqlResult(List.of("result"), List.of(List.of(n + " row(s) affected")), null);
            }
            try (ResultSet rs = st.getResultSet()) {
                return read(rs);
            }
        } catch (SQLException e) {
            return SqlResult.error(firstLine(e.getMessage()));
        }
    }

    private SqlResult read(ResultSet rs) throws SQLException {
        ResultSetMetaData md = rs.getMetaData();
        int cols = md.getColumnCount();
        List<String> columns = new ArrayList<>();
        for (int i = 1; i <= cols; i++) {
            columns.add(md.getColumnLabel(i));
        }
        List<List<String>> rows = new ArrayList<>();
        int count = 0;
        while (rs.next() && count++ < MAX_ROWS) {
            List<String> row = new ArrayList<>(cols);
            for (int i = 1; i <= cols; i++) {
                String v = rs.getString(i);
                row.add(rs.wasNull() ? "NULL" : v);
            }
            rows.add(row);
        }
        return new SqlResult(columns, rows, null);
    }

    private boolean sameTable(SqlResult a, SqlResult b, boolean ordered) {
        if (a.columns().size() != b.columns().size() || a.rows().size() != b.rows().size()) {
            return false;
        }
        if (ordered) {
            return a.rows().equals(b.rows());
        }
        List<String> sa = a.rows().stream().map(Object::toString).sorted().toList();
        List<String> sb = b.rows().stream().map(Object::toString).sorted().toList();
        return sa.equals(sb);
    }

    private static String firstLine(String s) {
        if (s == null) {
            return "SQL error";
        }
        int nl = s.indexOf('\n');
        return nl < 0 ? s : s.substring(0, nl);
    }
}
