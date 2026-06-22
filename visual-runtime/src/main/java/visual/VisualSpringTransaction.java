package visual;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A dependency-free teaching model of Spring's {@code @Transactional} rollback
 * decision. It is not Spring itself: it shows the mental model interviewers
 * usually ask for:
 *
 * <ul>
 *   <li>a transaction starts before the proxied method body,</li>
 *   <li>{@code persist} stages data in the transaction, not as an already-final
 *       commit,</li>
 *   <li>the interceptor decides on commit or rollback after the method exits,</li>
 *   <li>by default {@code RuntimeException} and {@code Error} roll back, checked
 *       exceptions do not, unless a rollback rule says otherwise.</li>
 * </ul>
 *
 * <p>Every step emits {@link Trace} events so a topic visualizer can replay the
 * transaction state without bringing Spring or JPA onto the learner classpath.
 */
public class VisualSpringTransaction {

    private final String name;
    private final Map<String, String> database = new LinkedHashMap<>();
    private Transaction current;

    public VisualSpringTransaction() {
        this("orderService");
    }

    public VisualSpringTransaction(String name) {
        this.name = name;
        Trace.event("SPRING_TX_MANAGER_CREATED",
                "Created teaching transaction manager '" + name + "' with an empty committed database",
                "Создан учебный transaction manager '" + name + "' с пустой зафиксированной базой",
                List.of(), state());
    }

    /**
     * Starts a simulated {@code @Transactional} method call.
     */
    public Transaction transactional(String methodName) {
        if (current != null) {
            throw new IllegalStateException("A transaction is already active");
        }
        current = new Transaction(methodName);
        Trace.event("SPRING_TX_BEGIN",
                "@Transactional proxy opens a transaction before method '" + methodName + "' runs",
                "@Transactional proxy открывает транзакцию перед запуском метода '" + methodName + "'",
                List.of("tx"), state());
        return current;
    }

    /**
     * Simulates an external call made after the transactional method has already
     * returned and the transaction has already committed.
     */
    public void externalCallAfterCommitThrowsRuntime(String systemName, String exceptionName) {
        Trace.event("SPRING_EXTERNAL_EXCEPTION_AFTER_COMMIT",
                "External call '" + systemName + "' throws " + exceptionName
                        + " after the transactional method already committed; the committed row stays in the database",
                "Внешний вызов '" + systemName + "' бросает " + exceptionName
                        + " после того, как @Transactional метод уже зафиксировался; строка остаётся в базе",
                List.of("external:" + systemName), stateWithExternalFailure(systemName, exceptionName));
    }

    private boolean shouldRollback(SimException exception) {
        if (exception == null) {
            return false;
        }
        if (matches(current.noRollbackFor, exception)) {
            exception.decision = "commit";
            exception.matchedRule = "noRollbackFor";
            Trace.event("SPRING_TX_NO_ROLLBACK_FOR_MATCH",
                    exception.name + " matches noRollbackFor, so Spring will commit even though the exception propagated",
                    exception.name + " совпадает с noRollbackFor, поэтому Spring зафиксирует транзакцию, хотя exception вышел наружу",
                    List.of("exception", "rule:noRollbackFor:" + exception.name), state());
            return false;
        }
        if (matches(current.rollbackFor, exception)) {
            current.rollbackOnly = true;
            exception.decision = "rollback";
            exception.matchedRule = "rollbackFor";
            Trace.event("SPRING_TX_ROLLBACK_FOR_MATCH",
                    exception.name + " matches rollbackFor, so Spring marks the transaction rollback-only",
                    exception.name + " совпадает с rollbackFor, поэтому Spring помечает транзакцию rollback-only",
                    List.of("exception", "rollbackOnly", "rule:rollbackFor:" + exception.name), state());
            return true;
        }
        if ("runtime".equals(exception.kind) || "error".equals(exception.kind)) {
            current.rollbackOnly = true;
            exception.decision = "rollback";
            exception.matchedRule = "default";
            Trace.event("SPRING_TX_DEFAULT_ROLLBACK",
                    exception.name + " is an unchecked exception; by default Spring marks the transaction rollback-only",
                    exception.name + " является unchecked exception; по умолчанию Spring помечает транзакцию rollback-only",
                    List.of("exception", "rollbackOnly"), state());
            return true;
        }
        exception.decision = "commit";
        exception.matchedRule = "default";
        Trace.event("SPRING_TX_CHECKED_COMMIT",
                exception.name + " is checked and no rollbackFor rule matched; by default Spring may commit",
                exception.name + " является checked exception, и rollbackFor не совпал; по умолчанию Spring может зафиксировать транзакцию",
                List.of("exception"), state());
        return false;
    }

    private static boolean matches(Set<String> rules, SimException exception) {
        if (rules.contains(exception.name) || rules.contains(exception.kind)) {
            return true;
        }
        if (rules.contains("Exception") && !"error".equals(exception.kind)) {
            return true;
        }
        return rules.contains("Throwable");
    }

    private Object stateWithExternalFailure(String systemName, String exceptionName) {
        Map<String, Object> s = baseState();
        s.put("phase", "external_failed");
        s.put("rollbackOnly", false);
        s.put("staged", List.of());
        s.put("rules", rulesState(Set.of(), Set.of()));
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("name", exceptionName);
        e.put("kind", "runtime");
        e.put("source", systemName);
        e.put("decision", "none");
        e.put("matchedRule", "none");
        s.put("exception", e);
        return s;
    }

    /** Builds the JSON-serializable snapshot consumed by the visualizer. */
    private Object state() {
        Map<String, Object> s = baseState();
        if (current == null) {
            s.put("phase", "idle");
            s.put("rollbackOnly", false);
            s.put("staged", List.of());
            s.put("rules", rulesState(Set.of(), Set.of()));
            return s;
        }

        s.put("phase", current.phase);
        s.put("currentMethod", current.methodName);
        s.put("rollbackOnly", current.rollbackOnly);
        s.put("staged", rowList(current.staged));
        s.put("rules", rulesState(current.rollbackFor, current.noRollbackFor));
        if (current.exception != null) {
            s.put("exception", exceptionState(current.exception));
        }
        return s;
    }

    private Map<String, Object> baseState() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("name", name);
        s.put("database", databaseList());
        return s;
    }

    private List<Object> databaseList() {
        List<Object> rows = new ArrayList<>();
        for (Map.Entry<String, String> entry : database.entrySet()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", entry.getKey());
            row.put("value", entry.getValue());
            rows.add(row);
        }
        return rows;
    }

    private static List<Object> rowList(List<Row> staged) {
        List<Object> rows = new ArrayList<>();
        for (Row row : staged) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("id", row.id());
            r.put("value", row.value());
            rows.add(r);
        }
        return rows;
    }

    private static Map<String, Object> rulesState(Set<String> rollbackFor, Set<String> noRollbackFor) {
        Map<String, Object> rules = new LinkedHashMap<>();
        rules.put("rollbackFor", new ArrayList<>(rollbackFor));
        rules.put("noRollbackFor", new ArrayList<>(noRollbackFor));
        return rules;
    }

    private static Map<String, Object> exceptionState(SimException exception) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("name", exception.name);
        e.put("kind", exception.kind);
        e.put("source", exception.source);
        e.put("decision", exception.decision);
        e.put("matchedRule", exception.matchedRule);
        return e;
    }

    public final class Transaction {
        private final String methodName;
        private final List<Row> staged = new ArrayList<>();
        private final Set<String> rollbackFor = new LinkedHashSet<>();
        private final Set<String> noRollbackFor = new LinkedHashSet<>();
        private String phase = "active";
        private boolean rollbackOnly;
        private SimException exception;
        private boolean completed;

        private Transaction(String methodName) {
            this.methodName = methodName;
        }

        public Transaction rollbackFor(String exceptionName) {
            rollbackFor.add(exceptionName);
            Trace.event("SPRING_TX_ROLLBACK_RULE_ADDED",
                    "Configured rollbackFor = " + exceptionName + " for method '" + methodName + "'",
                    "Для метода '" + methodName + "' настроен rollbackFor = " + exceptionName,
                    List.of("rule:rollbackFor:" + exceptionName), state());
            return this;
        }

        public Transaction noRollbackFor(String exceptionName) {
            noRollbackFor.add(exceptionName);
            Trace.event("SPRING_TX_NO_ROLLBACK_RULE_ADDED",
                    "Configured noRollbackFor = " + exceptionName + " for method '" + methodName + "'",
                    "Для метода '" + methodName + "' настроен noRollbackFor = " + exceptionName,
                    List.of("rule:noRollbackFor:" + exceptionName), state());
            return this;
        }

        public Transaction persist(String id, String value) {
            ensureOpen();
            staged.add(new Row(id, value));
            Trace.event("SPRING_ENTITY_PERSISTED",
                    "persist(" + id + ") stages a row inside the transaction; it is not committed yet",
                    "persist(" + id + ") помещает строку внутрь транзакции; она ещё не зафиксирована",
                    List.of("staged:" + id), state());
            return this;
        }

        public Transaction externalCall(String systemName) {
            ensureOpen();
            Trace.event("SPRING_EXTERNAL_CALL",
                    "Method calls external system '" + systemName + "' while the transaction is still open",
                    "Метод вызывает внешнюю систему '" + systemName + "', пока транзакция ещё открыта",
                    List.of("external:" + systemName), state());
            return this;
        }

        public Transaction externalCallThrowsRuntime(String systemName, String exceptionName) {
            return failFrom(systemName, exceptionName, "runtime", "SPRING_EXTERNAL_CALL_FAILED",
                    "External system '" + systemName + "' throws " + exceptionName
                            + " before the transactional method returns",
                    "Внешняя система '" + systemName + "' бросает " + exceptionName
                            + " до выхода из @Transactional метода");
        }

        public Transaction externalCallThrowsChecked(String systemName, String exceptionName) {
            return failFrom(systemName, exceptionName, "checked", "SPRING_EXTERNAL_CALL_FAILED",
                    "External system '" + systemName + "' throws checked " + exceptionName
                            + " before the transactional method returns",
                    "Внешняя система '" + systemName + "' бросает checked " + exceptionName
                            + " до выхода из @Transactional метода");
        }

        public Transaction throwRuntime(String exceptionName) {
            return failFrom("method", exceptionName, "runtime", "SPRING_EXCEPTION_THROWN",
                    "Method exits by throwing unchecked " + exceptionName,
                    "Метод выходит, бросая unchecked " + exceptionName);
        }

        public Transaction throwChecked(String exceptionName) {
            return failFrom("method", exceptionName, "checked", "SPRING_EXCEPTION_THROWN",
                    "Method exits by throwing checked " + exceptionName,
                    "Метод выходит, бросая checked " + exceptionName);
        }

        public Transaction throwError(String errorName) {
            return failFrom("method", errorName, "error", "SPRING_EXCEPTION_THROWN",
                    "Method exits by throwing " + errorName + ", which behaves like an Error for rollback rules",
                    "Метод выходит, бросая " + errorName + ", который для rollback rules ведёт себя как Error");
        }

        public boolean complete() {
            ensureOpen();
            boolean rollback = shouldRollback(exception);
            if (rollback) {
                List<String> ids = new ArrayList<>();
                for (Row row : staged) {
                    ids.add(row.id());
                }
                staged.clear();
                phase = "rolled_back";
                Trace.event("SPRING_TX_ROLLED_BACK",
                        "Transaction for '" + methodName + "' rolls back; staged rows " + ids + " are discarded",
                        "Транзакция метода '" + methodName + "' откатывается; подготовленные строки " + ids + " отбрасываются",
                        List.of("tx", "rollbackOnly"), state());
                completed = true;
                current = null;
                return false;
            }

            for (Row row : staged) {
                database.put(row.id(), row.value());
            }
            staged.clear();
            phase = "committed";
            Trace.event("SPRING_TX_COMMITTED",
                    "Transaction for '" + methodName + "' commits; staged rows become durable database rows",
                    "Транзакция метода '" + methodName + "' фиксируется; подготовленные строки становятся сохранёнными строками базы",
                    List.of("tx"), state());
            completed = true;
            current = null;
            return true;
        }

        private Transaction failFrom(String source, String exceptionName, String kind,
                                     String event, String descEn, String descRu) {
            ensureOpen();
            exception = new SimException(exceptionName, kind, source);
            Trace.event(event, descEn, descRu,
                    List.of("exception", "external:" + source), state());
            return this;
        }

        private void ensureOpen() {
            if (completed) {
                throw new IllegalStateException("Transaction already completed");
            }
        }
    }

    private record Row(String id, String value) {
    }

    private static final class SimException {
        final String name;
        final String kind;
        final String source;
        String decision = "pending";
        String matchedRule = "none";

        SimException(String name, String kind, String source) {
            this.name = name;
            this.kind = kind;
            this.source = source;
        }
    }
}
