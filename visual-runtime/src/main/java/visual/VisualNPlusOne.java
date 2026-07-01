package visual;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A deterministic teaching model for the ORM "N+1 select" problem. It simulates
 * loading a list of parent rows and then their child collections, counting the
 * SQL statements each fetch strategy fires: lazy access (1 root query + one
 * query per parent = N+1), a single JOIN FETCH, a JPA entity graph, or lazy
 * access grouped by a Hibernate batch size (1 root query + ceil(N / batchSize)).
 *
 * <p>Nothing here talks to a real database; every query is a labelled string in
 * a running log, so examples stay offline and reproducible.
 */
public class VisualNPlusOne {

    private final String scene;
    private final List<Parent> parents = new ArrayList<>();
    private final List<Map<String, Object>> queries = new ArrayList<>();

    private String strategy = "LAZY";
    private int batchSize = 0;
    private String parentTable = "";
    private String childTable = "";

    public VisualNPlusOne() {
        this("n-plus-one");
    }

    public VisualNPlusOne(String scene) {
        this.scene = Objects.requireNonNull(scene, "scene");
        Trace.event("N_PLUS_ONE_CREATED",
                "Created scene '" + scene + "' to count SQL statements per fetch strategy",
                "Создана сцена '" + scene + "' для подсчёта SQL-запросов по стратегиям загрузки",
                List.of("counter"),
                state());
    }

    /**
     * Loads the parent rows lazily: one root SELECT, child collections untouched.
     */
    public VisualNPlusOne loadParents(String parentTable, String childTable,
                                      int parentCount, int childrenEach) {
        prepare("LAZY", parentTable, childTable, parentCount, childrenEach);
        addQuery("root", "SELECT * FROM " + parentTable);
        Trace.event("ROOT_QUERY",
                "Root query loaded " + parentCount + " " + parentTable
                        + " rows; child collections are still proxies",
                "Корневой запрос загрузил строк (" + parentTable + "): " + parentCount
                        + "; коллекции детей пока прокси",
                List.of("query:root"),
                state());
        return this;
    }

    /**
     * Loads parents and their children in a single JOIN FETCH query.
     */
    public VisualNPlusOne loadParentsWithJoinFetch(String parentTable, String childTable,
                                                   int parentCount, int childrenEach) {
        prepare("JOIN_FETCH", parentTable, childTable, parentCount, childrenEach);
        markAllLoaded();
        addQuery("joinfetch", "SELECT * FROM " + parentTable
                + " p JOIN " + childTable + " c ON c." + fk() + " = p.id");
        Trace.event("JOIN_FETCH_QUERY",
                "One JOIN FETCH loaded " + parentTable + " and " + childTable + " together",
                "Один JOIN FETCH загрузил " + parentTable + " и " + childTable + " вместе",
                List.of("query:joinfetch"),
                state());
        return this;
    }

    /**
     * Loads parents and children with a JPA entity graph (also one query).
     */
    public VisualNPlusOne loadParentsWithEntityGraph(String parentTable, String childTable,
                                                     int parentCount, int childrenEach) {
        prepare("ENTITY_GRAPH", parentTable, childTable, parentCount, childrenEach);
        markAllLoaded();
        addQuery("entitygraph", "SELECT * FROM " + parentTable
                + " p LEFT JOIN " + childTable + " c ON c." + fk() + " = p.id /* @EntityGraph */");
        Trace.event("ENTITY_GRAPH_QUERY",
                "An entity graph told Hibernate to fetch " + childTable + " in the same query",
                "Entity graph указал Hibernate загрузить " + childTable + " в том же запросе",
                List.of("query:entitygraph"),
                state());
        return this;
    }

    /**
     * Enables Hibernate-style batch fetching for subsequent lazy access.
     */
    public VisualNPlusOne setBatchSize(int batchSize) {
        if (batchSize < 1) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        this.batchSize = batchSize;
        Trace.event("BATCH_SIZE_SET",
                "Batch size set to " + batchSize + ": lazy children load in groups",
                "Batch size = " + batchSize + ": ленивые дети грузятся группами",
                List.of("counter"),
                state());
        return this;
    }

    /**
     * Touches every parent's child collection. Behaviour depends on the strategy:
     * already-fetched collections fire nothing; otherwise a per-parent select
     * (the N+1) or one select per batch when a batch size is configured.
     */
    public VisualNPlusOne accessAllChildren() {
        List<Parent> pending = new ArrayList<>();
        for (Parent p : parents) {
            if (!p.loaded) {
                pending.add(p);
            }
        }

        if (pending.isEmpty()) {
            Trace.event("COLLECTION_ALREADY_LOADED",
                    "Every " + childTable + " collection was already fetched: no extra query",
                    "Все коллекции " + childTable + " уже загружены: ни одного лишнего запроса",
                    List.of("counter"),
                    state());
            return this;
        }

        if (batchSize > 0) {
            for (int i = 0; i < pending.size(); i += batchSize) {
                List<Parent> batch = pending.subList(i, Math.min(i + batchSize, pending.size()));
                StringBuilder ids = new StringBuilder();
                for (int j = 0; j < batch.size(); j++) {
                    if (j > 0) {
                        ids.append(", ");
                    }
                    ids.append(batch.get(j).id);
                    batch.get(j).loaded = true;
                }
                addQuery("batch", "SELECT * FROM " + childTable
                        + " WHERE " + fk() + " IN (" + ids + ")");
                Trace.event("BATCH_QUERY",
                        "One batch query loaded " + childTable + " for "
                                + batch.size() + " parent(s) via IN (...)",
                        "Один batch-запрос загрузил " + childTable + " для "
                                + batch.size() + " родителя(ей) через IN (...)",
                        List.of("query:batch"),
                        state());
            }
        } else {
            for (Parent p : pending) {
                p.loaded = true;
                addQuery("nplus1", "SELECT * FROM " + childTable
                        + " WHERE " + fk() + " = " + p.id);
                Trace.event("N_PLUS_ONE_QUERY",
                        "Lazy access to " + p.label + " fired one more SELECT (the +1)",
                        "Ленивое обращение к " + p.label + " вызвало ещё один SELECT (это +1)",
                        List.of("query:nplus1", "parent:" + p.id),
                        state());
            }
        }
        return this;
    }

    private void prepare(String strategy, String parentTable, String childTable,
                         int parentCount, int childrenEach) {
        Objects.requireNonNull(parentTable, "parentTable");
        Objects.requireNonNull(childTable, "childTable");
        if (parentCount < 1) {
            throw new IllegalArgumentException("parentCount must be positive");
        }
        if (childrenEach < 0) {
            throw new IllegalArgumentException("childrenEach must not be negative");
        }
        this.strategy = strategy;
        this.parentTable = parentTable;
        this.childTable = childTable;
        parents.clear();
        for (int i = 1; i <= parentCount; i++) {
            parents.add(new Parent(i, parentTable + " #" + i, childrenEach));
        }
    }

    private void markAllLoaded() {
        for (Parent p : parents) {
            p.loaded = true;
        }
    }

    private String fk() {
        String singular = parentTable.endsWith("s")
                ? parentTable.substring(0, parentTable.length() - 1)
                : parentTable;
        return singular + "_id";
    }

    private void addQuery(String kind, String sql) {
        Map<String, Object> q = new LinkedHashMap<>();
        q.put("n", queries.size() + 1);
        q.put("kind", kind);
        q.put("sql", sql);
        queries.add(q);
    }

    private Object state() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("scene", scene);
        s.put("strategy", strategy);
        s.put("batchSize", batchSize);
        s.put("parentTable", parentTable);
        s.put("childTable", childTable);
        s.put("queryCount", queries.size());

        List<Object> parentList = new ArrayList<>();
        for (Parent p : parents) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", p.id);
            item.put("label", p.label);
            item.put("childCount", p.childCount);
            item.put("loaded", p.loaded);
            parentList.add(item);
        }
        s.put("parents", parentList);
        s.put("queries", new ArrayList<>(queries));
        return s;
    }

    private static final class Parent {
        final int id;
        final String label;
        final int childCount;
        boolean loaded;

        Parent(int id, String label, int childCount) {
            this.id = id;
            this.label = label;
            this.childCount = childCount;
        }
    }
}
