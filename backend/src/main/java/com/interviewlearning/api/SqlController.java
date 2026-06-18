package com.interviewlearning.api;

import com.interviewlearning.sql.SqlDtos.SqlResult;
import com.interviewlearning.sql.SqlDtos.SqlRunResponse;
import com.interviewlearning.sql.SqlService;
import com.interviewlearning.topics.TopicDtos.Mission;
import com.interviewlearning.topics.TopicDtos.ProjectFile;
import com.interviewlearning.topics.TopicDtos.TopicDetail;
import com.interviewlearning.topics.TopicRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Runs a user query for a SQL topic against the topic's seeded schema and reports
 * the result table plus which SQL missions it satisfies (by comparing the result
 * to each mission's reference {@code expectedSql}).
 */
@RestController
@RequestMapping("/api/sql")
public class SqlController {

    private final TopicRepository topics;
    private final SqlService sql;

    public SqlController(TopicRepository topics, SqlService sql) {
        this.topics = topics;
        this.sql = sql;
    }

    public record SqlRequest(String topicId, String sql) {
    }

    @PostMapping
    public SqlRunResponse run(@RequestBody SqlRequest request) {
        Optional<TopicDetail> opt = request.topicId() == null
                ? Optional.empty()
                : topics.getTopic(request.topicId());
        if (opt.isEmpty() || !"sql".equals(opt.get().mode())) {
            return new SqlRunResponse(List.of(), List.of(), "Not a SQL topic.", Map.of());
        }
        TopicDetail topic = opt.get();
        String schema = schemaOf(topic);
        String userSql = request.sql() == null ? "" : request.sql();

        SqlResult result = sql.run(schema, userSql);

        Map<String, Boolean> missions = new LinkedHashMap<>();
        for (Mission m : topic.missions()) {
            if (!"sql".equals(m.type())) {
                continue;
            }
            String expected = expectedSqlOf(m);
            boolean pass = result.error() == null
                    && expected != null
                    && sql.matches(schema, userSql, expected, orderedOf(m));
            missions.put(m.id(), pass);
        }
        return new SqlRunResponse(result.columns(), result.rows(), result.error(), missions);
    }

    private String schemaOf(TopicDetail topic) {
        for (ProjectFile f : topic.starterFiles()) {
            if (f.path() != null && f.path().endsWith("schema.sql")) {
                return f.content();
            }
        }
        return "";
    }

    private String expectedSqlOf(Mission m) {
        Map<?, ?> rule = sqlRule(m);
        if (rule == null) {
            return null;
        }
        Object e = rule.get("expectedSql");
        return e == null ? null : String.valueOf(e);
    }

    private boolean orderedOf(Mission m) {
        Map<?, ?> rule = sqlRule(m);
        return rule != null && "true".equals(String.valueOf(rule.get("ordered")));
    }

    /** Finds the {@code { kind: sqlResult, ... }} predicate in a mission's requires. */
    private Map<?, ?> sqlRule(Mission m) {
        for (Object o : m.requires()) {
            if (o instanceof Map<?, ?> map && "sqlResult".equals(String.valueOf(map.get("kind")))) {
                return map;
            }
        }
        return null;
    }
}
