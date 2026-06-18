package com.interviewlearning.sql;

import java.util.List;
import java.util.Map;

/** DTOs for the SQL-topic playground. */
public final class SqlDtos {

    private SqlDtos() {
    }

    /**
     * The result of running one query.
     *
     * @param columns column names in select order (empty for a non-SELECT / error)
     * @param rows    each row's values as strings, in column order
     * @param error   error message, or null on success
     */
    public record SqlResult(List<String> columns, List<List<String>> rows, String error) {
        public static SqlResult error(String message) {
            return new SqlResult(List.of(), List.of(), message);
        }
    }

    /** Endpoint response: the result table plus per-mission pass flags. */
    public record SqlRunResponse(
            List<String> columns,
            List<List<String>> rows,
            String error,
            Map<String, Boolean> missions
    ) {
    }
}
