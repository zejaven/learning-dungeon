package visual;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A dependency-free teaching model of the JPA entity lifecycle. It does not
 * implement JPA; it simulates the states an interviewer cares about and emits
 * trace events for the frontend: transient, managed, detached and removed.
 */
public class VisualJpaEntityLifecycle {

    public enum EntityState {
        TRANSIENT,
        MANAGED,
        DETACHED,
        REMOVED
    }

    private final String contextName;
    private final List<Entity> entities = new ArrayList<>();
    private final Map<Long, Row> databaseRows = new LinkedHashMap<>();
    private final List<Map<String, Object>> sqlLog = new ArrayList<>();

    private long nextId = 1;
    private boolean open = true;

    public VisualJpaEntityLifecycle() {
        this("entityManager");
    }

    public VisualJpaEntityLifecycle(String contextName) {
        this.contextName = Objects.requireNonNull(contextName, "contextName");
        Trace.event("JPA_CONTEXT_CREATED",
                "Opened Persistence Context '" + contextName + "'; no entity is managed yet",
                "Открыт Persistence Context '" + contextName + "'; пока ни одна сущность не managed",
                List.of("context"),
                state("new EntityManager"));
    }

    /**
     * Adds a deterministic database row for examples that start from find().
     * This is a setup helper, not a JPA operation, so it emits no trace event.
     */
    public VisualJpaEntityLifecycle seedRow(long id, String label) {
        if (id < 1) {
            throw new IllegalArgumentException("id must be positive");
        }
        databaseRows.put(id, new Row(id, Objects.requireNonNull(label, "label")));
        if (id >= nextId) {
            nextId = id + 1;
        }
        return this;
    }

    public Entity newEntity(String handle, String label) {
        Entity entity = new Entity(handle, label, null, EntityState.TRANSIENT);
        entities.add(entity);
        Trace.event("JPA_ENTITY_TRANSIENT",
                "new " + label + " exists only as a Java object; EntityManager does not track it",
                "new " + label + " существует только как Java-объект; EntityManager его не отслеживает",
                List.of("state:TRANSIENT", "entity:" + entity.handle),
                state("new Entity"));
        return entity;
    }

    public Entity find(String handle, long id) {
        requireOpen();
        Row row = databaseRows.get(id);
        if (row == null || row.deleted) {
            throw new IllegalArgumentException("No active row with id " + id);
        }

        Entity alreadyManaged = managedById(id);
        if (alreadyManaged != null) {
            Trace.event("JPA_FIND",
                    "find(" + id + ") returned the already managed instance from Persistence Context",
                    "find(" + id + ") вернул уже managed-экземпляр из Persistence Context",
                    List.of("state:MANAGED", "entity:" + alreadyManaged.handle, "db:" + id),
                    state("find(" + id + ")"));
            return alreadyManaged;
        }

        Entity entity = new Entity(handle, row.label, id, EntityState.MANAGED);
        entities.add(entity);
        Trace.event("JPA_FIND",
                "find(" + id + ") loaded a database row and put it into managed state",
                "find(" + id + ") загрузил строку из базы и перевёл сущность в managed",
                List.of("state:MANAGED", "entity:" + entity.handle, "db:" + id),
                state("find(" + id + ")"));
        return entity;
    }

    public void persist(Entity entity) {
        requireOpen();
        requireState(entity, EntityState.TRANSIENT, "persist works with a transient entity in this model");
        if (entity.id == null) {
            entity.id = nextId++;
        }
        entity.state = EntityState.MANAGED;
        entity.pendingAction = "INSERT";
        entity.changed = false;
        Trace.event("JPA_PERSIST",
                "persist(" + entity.handle + ") made the transient object managed and scheduled INSERT",
                "persist(" + entity.handle + ") перевёл transient-объект в managed и запланировал INSERT",
                List.of("state:MANAGED", "entity:" + entity.handle),
                state("persist(" + entity.handle + ")"));
    }

    public void change(Entity entity, String newLabel) {
        Objects.requireNonNull(entity, "entity");
        if (entity.state == EntityState.REMOVED) {
            throw new IllegalStateException("Cannot change a removed entity");
        }
        entity.label = Objects.requireNonNull(newLabel, "newLabel");
        entity.changed = true;

        boolean tracked = open && entity.state == EntityState.MANAGED;
        if (tracked && entity.pendingAction.isEmpty()) {
            entity.pendingAction = "UPDATE";
        }

        String descEn = tracked
                ? "Changed managed " + entity.handle + "; dirty checking will flush an UPDATE"
                : "Changed " + entity.handle + " outside tracking; no SQL is scheduled until merge/persist";
        String descRu = tracked
                ? "Изменён managed " + entity.handle + "; dirty checking подготовит UPDATE при flush"
                : "Изменён " + entity.handle + " вне отслеживания; SQL не появится до merge/persist";

        Trace.event("JPA_CHANGE",
                descEn,
                descRu,
                List.of("state:" + entity.state.name(), "entity:" + entity.handle),
                state("change(" + entity.handle + ")"));
    }

    public void detach(Entity entity) {
        requireOpen();
        requireState(entity, EntityState.MANAGED, "detach requires a managed entity");
        entity.state = EntityState.DETACHED;
        entity.pendingAction = "";
        Trace.event("JPA_DETACH",
                "detach(" + entity.handle + ") removed it from Persistence Context; the Java object remains",
                "detach(" + entity.handle + ") убрал сущность из Persistence Context; Java-объект остался",
                List.of("state:DETACHED", "entity:" + entity.handle),
                state("detach(" + entity.handle + ")"));
    }

    public void clear() {
        requireOpen();
        int detached = detachTrackedEntities();
        Trace.event("JPA_CLEAR",
                "clear() detached " + detached + " tracked entity instance(s) at once",
                "clear() разом перевёл в detached отслеживаемые экземпляры: " + detached,
                List.of("state:DETACHED"),
                state("clear()"));
    }

    public void close() {
        requireOpen();
        int detached = detachTrackedEntities();
        open = false;
        Trace.event("JPA_CLOSE",
                "close() ended the Persistence Context; " + detached + " tracked instance(s) became detached",
                "close() завершил Persistence Context; tracked-экземпляры стали detached: " + detached,
                List.of("state:DETACHED", "context"),
                state("close()"));
    }

    public Entity merge(Entity source, String managedHandle) {
        requireOpen();
        Objects.requireNonNull(source, "source");
        if (source.state == EntityState.REMOVED) {
            throw new IllegalStateException("Cannot merge a removed entity");
        }

        Long id = source.id;
        boolean newObject = id == null;
        if (newObject) {
            id = nextId++;
        }

        Entity managed = managedById(id);
        if (managed == null) {
            managed = new Entity(managedHandle, source.label, id, EntityState.MANAGED);
            entities.add(managed);
        } else {
            managed.label = source.label;
        }

        managed.changed = source.changed;
        managed.pendingAction = newObject || !databaseRows.containsKey(id) ? "INSERT" : "UPDATE";

        Trace.event("JPA_MERGE",
                "merge(" + source.handle + ") copied state into managed " + managed.handle
                        + "; the original object stays " + source.state.name().toLowerCase(),
                "merge(" + source.handle + ") скопировал состояние в managed " + managed.handle
                        + "; исходный объект остался " + source.state.name().toLowerCase(),
                List.of("state:" + source.state.name(), "state:MANAGED",
                        "entity:" + source.handle, "entity:" + managed.handle),
                state("merge(" + source.handle + ")"));
        return managed;
    }

    public void remove(Entity entity) {
        requireOpen();
        requireState(entity, EntityState.MANAGED, "remove requires a managed entity");
        entity.state = EntityState.REMOVED;
        entity.pendingAction = "DELETE";
        entity.changed = false;
        Trace.event("JPA_REMOVE",
                "remove(" + entity.handle + ") marked the managed entity as removed; DELETE waits for flush",
                "remove(" + entity.handle + ") пометил managed-сущность как removed; DELETE ждёт flush",
                List.of("state:REMOVED", "entity:" + entity.handle),
                state("remove(" + entity.handle + ")"));
    }

    public void flush() {
        requireOpen();
        int changes = 0;
        List<String> highlight = new ArrayList<>();
        for (Entity entity : entities) {
            if ("INSERT".equals(entity.pendingAction)) {
                databaseRows.put(entity.id, new Row(entity.id, entity.label));
                addSql("insert", "INSERT INTO entities (id, label) VALUES ("
                        + entity.id + ", '" + escapeSql(entity.label) + "')");
                entity.pendingAction = "";
                entity.changed = false;
                changes++;
                highlight.add("entity:" + entity.handle);
                highlight.add("db:" + entity.id);
            } else if ("UPDATE".equals(entity.pendingAction)) {
                Row row = databaseRows.get(entity.id);
                if (row == null) {
                    row = new Row(entity.id, entity.label);
                    databaseRows.put(entity.id, row);
                } else {
                    row.label = entity.label;
                    row.deleted = false;
                }
                addSql("update", "UPDATE entities SET label = '" + escapeSql(entity.label)
                        + "' WHERE id = " + entity.id);
                entity.pendingAction = "";
                entity.changed = false;
                changes++;
                highlight.add("entity:" + entity.handle);
                highlight.add("db:" + entity.id);
            } else if ("DELETE".equals(entity.pendingAction)) {
                Row row = databaseRows.get(entity.id);
                if (row != null) {
                    row.deleted = true;
                }
                addSql("delete", "DELETE FROM entities WHERE id = " + entity.id);
                entity.pendingAction = "";
                changes++;
                highlight.add("entity:" + entity.handle);
                highlight.add("db:" + entity.id);
            }
        }
        if (highlight.isEmpty()) {
            highlight.add("context");
        }
        Trace.event("JPA_FLUSH",
                "flush() synchronized " + changes + " pending change(s) with the database",
                "flush() синхронизировал с базой pending-изменения: " + changes,
                highlight,
                state("flush()"));
    }

    private int detachTrackedEntities() {
        int count = 0;
        for (Entity entity : entities) {
            if (entity.state == EntityState.MANAGED || entity.state == EntityState.REMOVED) {
                entity.state = EntityState.DETACHED;
                entity.pendingAction = "";
                count++;
            }
        }
        return count;
    }

    private Entity managedById(long id) {
        for (Entity entity : entities) {
            if (entity.state == EntityState.MANAGED && entity.id != null && entity.id == id) {
                return entity;
            }
        }
        return null;
    }

    private void requireOpen() {
        if (!open) {
            throw new IllegalStateException("Persistence Context is closed");
        }
    }

    private static void requireState(Entity entity, EntityState expected, String message) {
        Objects.requireNonNull(entity, "entity");
        if (entity.state != expected) {
            throw new IllegalStateException(message + "; actual state is " + entity.state);
        }
    }

    private void addSql(String kind, String sql) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("n", sqlLog.size() + 1);
        item.put("kind", kind);
        item.put("sql", sql);
        sqlLog.add(item);
    }

    private static String escapeSql(String value) {
        return value.replace("'", "''");
    }

    private Object state(String operation) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("contextName", contextName);
        s.put("contextOpen", open);
        s.put("operation", operation);
        s.put("managedCount", countManaged());

        List<Object> entityList = new ArrayList<>();
        for (Entity entity : entities) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("handle", entity.handle);
            item.put("label", entity.label);
            item.put("id", entity.id == null ? "null" : String.valueOf(entity.id));
            item.put("state", entity.state.name());
            item.put("changed", entity.changed);
            item.put("pendingAction", entity.pendingAction);
            entityList.add(item);
        }
        s.put("entities", entityList);

        List<Object> rows = new ArrayList<>();
        for (Row row : databaseRows.values()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", row.id);
            item.put("label", row.label);
            item.put("deleted", row.deleted);
            rows.add(item);
        }
        s.put("databaseRows", rows);
        s.put("sqlLog", new ArrayList<>(sqlLog));
        return s;
    }

    private int countManaged() {
        int count = 0;
        for (Entity entity : entities) {
            if (entity.state == EntityState.MANAGED) {
                count++;
            }
        }
        return count;
    }

    public static final class Entity {
        private final String handle;
        private String label;
        private Long id;
        private EntityState state;
        private boolean changed;
        private String pendingAction = "";

        private Entity(String handle, String label, Long id, EntityState state) {
            this.handle = Objects.requireNonNull(handle, "handle");
            this.label = Objects.requireNonNull(label, "label");
            this.id = id;
            this.state = state;
        }

        public String handle() {
            return handle;
        }

        public String label() {
            return label;
        }

        public Long id() {
            return id;
        }

        public EntityState state() {
            return state;
        }
    }

    private static final class Row {
        final long id;
        String label;
        boolean deleted;

        Row(long id, String label) {
            this.id = id;
            this.label = label;
        }
    }
}
