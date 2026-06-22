package visual;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A dependency-free teaching model of Spring's {@code @TransactionalEventListener}.
 * It shows how an event published inside a transaction is deferred to a
 * transaction phase instead of being handled immediately.
 *
 * <p>The model intentionally keeps event matching simple: every registered
 * listener receives every published event. The learning point is the timing:
 * {@code BEFORE_COMMIT}, {@code AFTER_COMMIT}, {@code AFTER_ROLLBACK},
 * {@code AFTER_COMPLETION}, and {@code fallbackExecution}.
 */
public class VisualTransactionalEventListener {

    public enum Phase {
        BEFORE_COMMIT,
        AFTER_COMMIT,
        AFTER_ROLLBACK,
        AFTER_COMPLETION
    }

    private final String name;
    private final Map<String, String> database = new LinkedHashMap<>();
    private final List<Listener> listeners = new ArrayList<>();
    private List<DomainEvent> lastNoTransactionEvents = List.of();
    private List<Delivery> lastNoTransactionDeliveries = List.of();
    private Failure lastFailure;
    private Transaction current;

    public VisualTransactionalEventListener() {
        this("application");
    }

    public VisualTransactionalEventListener(String name) {
        this.name = name;
        Trace.event("TX_EVENT_MODEL_CREATED",
                "Created teaching application '" + name + "' with no active transaction",
                "Создано учебное приложение '" + name + "' без активной транзакции",
                List.of(), state());
    }

    public VisualTransactionalEventListener listener(String listenerName, Phase phase) {
        return listener(listenerName, phase, false);
    }

    public VisualTransactionalEventListener listener(String listenerName, Phase phase, boolean fallbackExecution) {
        clearNoTransactionView();
        listeners.add(new Listener(listenerName, phase, fallbackExecution));
        Trace.event("TX_EVENT_LISTENER_REGISTERED",
                "Registered listener '" + listenerName + "' for phase " + phase
                        + (fallbackExecution ? " with fallbackExecution=true" : ""),
                "Зарегистрирован listener '" + listenerName + "' для фазы " + phase
                        + (fallbackExecution ? " с fallbackExecution=true" : ""),
                List.of("listener:" + listenerName, "phase:" + phase),
                state());
        return this;
    }

    public Transaction transactional(String methodName) {
        if (current != null) {
            throw new IllegalStateException("A transaction is already active");
        }
        clearNoTransactionView();
        current = new Transaction(methodName);
        Trace.event("TX_EVENT_BEGIN",
                "@Transactional proxy opens transaction for method '" + methodName + "'",
                "@Transactional proxy открывает транзакцию для метода '" + methodName + "'",
                List.of("tx"), state());
        return current;
    }

    public void publishOutsideTransaction(String eventName) {
        if (current != null) {
            current.publish(eventName);
            return;
        }
        DomainEvent event = new DomainEvent(eventName, "no_transaction");
        List<Delivery> deliveries = deliveriesFor(event);
        lastNoTransactionEvents = List.of(event);
        lastNoTransactionDeliveries = deliveries;
        lastFailure = null;

        Trace.event("TX_EVENT_PUBLISHED",
                "Published event '" + eventName + "' while no transaction is active",
                "Опубликован event '" + eventName + "', когда активной транзакции нет",
                List.of("event:" + eventName),
                state());

        for (Delivery delivery : deliveries) {
            if (delivery.listener.fallbackExecution) {
                delivery.status = "done";
                delivery.listener.invocations++;
                Trace.event("TX_EVENT_FALLBACK_EXECUTED",
                        "Listener '" + delivery.listener.name + "' has fallbackExecution=true, so it runs immediately without a transaction",
                        "У listener '" + delivery.listener.name + "' fallbackExecution=true, поэтому он выполняется сразу без транзакции",
                        List.of("listener:" + delivery.listener.name, "delivery:" + delivery.id),
                        state());
            } else {
                delivery.status = "skipped";
                Trace.event("TX_EVENT_NO_TRANSACTION_SKIPPED",
                        "Listener '" + delivery.listener.name + "' is skipped because no transaction is active and fallbackExecution=false",
                        "Listener '" + delivery.listener.name + "' пропущен: активной транзакции нет и fallbackExecution=false",
                        List.of("listener:" + delivery.listener.name, "delivery:" + delivery.id),
                        state());
            }
        }
    }

    private List<Delivery> deliveriesFor(DomainEvent event) {
        List<Delivery> deliveries = new ArrayList<>();
        for (Listener listener : listeners) {
            deliveries.add(new Delivery(event, listener));
        }
        return deliveries;
    }

    private void clearNoTransactionView() {
        lastNoTransactionEvents = List.of();
        lastNoTransactionDeliveries = List.of();
        lastFailure = null;
    }

    private Object state() {
        if (current != null) {
            return current.state();
        }

        Map<String, Object> s = baseState();
        s.put("phase", lastNoTransactionEvents.isEmpty() ? "idle" : "no_transaction");
        s.put("completion", "none");
        s.put("staged", List.of());
        s.put("publishedEvents", eventState(lastNoTransactionEvents));
        s.put("deliveries", deliveryState(lastNoTransactionDeliveries));
        if (lastFailure != null) {
            s.put("failure", failureState(lastFailure));
        }
        return s;
    }

    private Map<String, Object> baseState() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("name", name);
        s.put("database", databaseList());
        s.put("listeners", listenerState());
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

    private List<Object> listenerState() {
        List<Object> result = new ArrayList<>();
        for (Listener listener : listeners) {
            Map<String, Object> l = new LinkedHashMap<>();
            l.put("name", listener.name);
            l.put("phase", listener.phase.name());
            l.put("fallbackExecution", listener.fallbackExecution);
            l.put("invocations", listener.invocations);
            l.put("failed", listener.failed);
            result.add(l);
        }
        return result;
    }

    private static List<Object> rowState(List<Row> rows) {
        List<Object> result = new ArrayList<>();
        for (Row row : rows) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("id", row.id());
            r.put("value", row.value());
            result.add(r);
        }
        return result;
    }

    private static List<Object> eventState(List<DomainEvent> events) {
        List<Object> result = new ArrayList<>();
        for (DomainEvent event : events) {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("name", event.name);
            e.put("status", event.status);
            result.add(e);
        }
        return result;
    }

    private static List<Object> deliveryState(List<Delivery> deliveries) {
        List<Object> result = new ArrayList<>();
        for (Delivery delivery : deliveries) {
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("id", delivery.id);
            d.put("event", delivery.event.name);
            d.put("listener", delivery.listener.name);
            d.put("phase", delivery.listener.phase.name());
            d.put("status", delivery.status);
            d.put("fallbackExecution", delivery.listener.fallbackExecution);
            result.add(d);
        }
        return result;
    }

    private static Map<String, Object> failureState(Failure failure) {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("listener", failure.listenerName);
        f.put("phase", failure.phase.name());
        f.put("exception", failure.exceptionName);
        f.put("effect", failure.effect);
        return f;
    }

    public final class Transaction {
        private final String methodName;
        private final List<Row> staged = new ArrayList<>();
        private final List<DomainEvent> events = new ArrayList<>();
        private final List<Delivery> deliveries = new ArrayList<>();
        private final Map<String, String> failuresByListener = new LinkedHashMap<>();
        private String phase = "active";
        private String completion = "none";
        private Failure failure;
        private boolean completed;

        private Transaction(String methodName) {
            this.methodName = methodName;
        }

        public Transaction persist(String id, String value) {
            ensureOpen();
            staged.add(new Row(id, value));
            Trace.event("TX_EVENT_ENTITY_PERSISTED",
                    "persist(" + id + ") stages a row inside the open transaction",
                    "persist(" + id + ") подготавливает строку внутри открытой транзакции",
                    List.of("tx", "staged:" + id),
                    state());
            return this;
        }

        public Transaction publish(String eventName) {
            ensureOpen();
            DomainEvent event = new DomainEvent(eventName, "queued");
            events.add(event);
            deliveries.addAll(deliveriesFor(event));
            Trace.event("TX_EVENT_PUBLISHED",
                    "Published event '" + eventName + "' inside the transaction; matching listeners are queued by phase",
                    "Event '" + eventName + "' опубликован внутри транзакции; подходящие listeners поставлены в очередь по фазам",
                    List.of("event:" + eventName),
                    state());
            return this;
        }

        public Transaction listenerFails(String listenerName, String exceptionName) {
            ensureOpen();
            failuresByListener.put(listenerName, exceptionName);
            Trace.event("TX_EVENT_LISTENER_FAILURE_ARMED",
                    "Listener '" + listenerName + "' is configured to throw " + exceptionName + " when its phase runs",
                    "Listener '" + listenerName + "' настроен бросить " + exceptionName + ", когда наступит его фаза",
                    List.of("listener:" + listenerName),
                    state());
            return this;
        }

        public boolean commit() {
            ensureOpen();
            phase = "before_commit";
            if (!dispatch(Phase.BEFORE_COMMIT)) {
                return rollbackInternal("beforeCommit listener failed");
            }

            markSkipped(Phase.AFTER_ROLLBACK);
            Set<String> committedIds = new LinkedHashSet<>();
            for (Row row : staged) {
                database.put(row.id(), row.value());
                committedIds.add(row.id());
            }
            staged.clear();
            completion = "commit";
            phase = "committed";
            events.forEach(e -> e.status = "committed");
            Trace.event("TX_EVENT_COMMITTED",
                    "Transaction for '" + methodName + "' commits; rows " + committedIds + " are now durable",
                    "Транзакция метода '" + methodName + "' фиксируется; строки " + committedIds + " теперь сохранены",
                    List.of("tx"),
                    state());

            dispatch(Phase.AFTER_COMMIT);
            phase = "after_completion";
            dispatch(Phase.AFTER_COMPLETION);
            completed = true;
            current = null;
            return true;
        }

        public boolean rollback(String reason) {
            ensureOpen();
            return rollbackInternal(reason);
        }

        private boolean rollbackInternal(String reason) {
            markSkipped(Phase.BEFORE_COMMIT);
            markSkipped(Phase.AFTER_COMMIT);
            Set<String> discardedIds = new LinkedHashSet<>();
            for (Row row : staged) {
                discardedIds.add(row.id());
            }
            staged.clear();
            completion = "rollback";
            phase = "rolled_back";
            events.forEach(e -> e.status = "rolled_back");
            Trace.event("TX_EVENT_ROLLED_BACK",
                    "Transaction for '" + methodName + "' rolls back because " + reason
                            + "; staged rows " + discardedIds + " are discarded",
                    "Транзакция метода '" + methodName + "' откатывается из-за " + reason
                            + "; подготовленные строки " + discardedIds + " отброшены",
                    List.of("tx"),
                    state());

            dispatch(Phase.AFTER_ROLLBACK);
            phase = "after_completion";
            dispatch(Phase.AFTER_COMPLETION);
            completed = true;
            current = null;
            return false;
        }

        private boolean dispatch(Phase phaseToRun) {
            boolean ok = true;
            for (Delivery delivery : deliveries) {
                if (delivery.listener.phase != phaseToRun || !"waiting".equals(delivery.status)) {
                    continue;
                }

                String exceptionName = failuresByListener.get(delivery.listener.name);
                if (exceptionName != null) {
                    delivery.status = "failed";
                    delivery.listener.failed = true;
                    String effect = phaseToRun == Phase.BEFORE_COMMIT
                            ? "rollback"
                            : "cannot_rollback_completed_transaction";
                    failure = new Failure(delivery.listener.name, phaseToRun, exceptionName, effect);
                    String eventType = phaseToRun == Phase.BEFORE_COMMIT
                            ? "TX_EVENT_BEFORE_COMMIT_FAILED"
                            : "TX_EVENT_AFTER_COMMIT_FAILED";
                    String descEn = phaseToRun == Phase.BEFORE_COMMIT
                            ? "BEFORE_COMMIT listener '" + delivery.listener.name + "' throws " + exceptionName
                            + "; Spring rolls the transaction back"
                            : phaseToRun + " listener '" + delivery.listener.name + "' throws " + exceptionName
                            + " after the transaction outcome is already decided";
                    String descRu = phaseToRun == Phase.BEFORE_COMMIT
                            ? "BEFORE_COMMIT listener '" + delivery.listener.name + "' бросает " + exceptionName
                            + "; Spring откатывает транзакцию"
                            : phaseToRun + " listener '" + delivery.listener.name + "' бросает " + exceptionName
                            + " после того, как исход транзакции уже решён";
                    Trace.event(eventType, descEn, descRu,
                            List.of("listener:" + delivery.listener.name, "delivery:" + delivery.id, "failure"),
                            state());
                    ok = phaseToRun != Phase.BEFORE_COMMIT;
                    if (!ok) {
                        return false;
                    }
                    continue;
                }

                delivery.status = "done";
                delivery.listener.invocations++;
                Trace.event(eventType(phaseToRun),
                        phaseToRun + " listener '" + delivery.listener.name + "' handles event '" + delivery.event.name + "'",
                        phaseToRun + " listener '" + delivery.listener.name + "' обрабатывает event '" + delivery.event.name + "'",
                        List.of("listener:" + delivery.listener.name, "delivery:" + delivery.id, "phase:" + phaseToRun),
                        state());
            }
            return ok;
        }

        private String eventType(Phase phase) {
            return switch (phase) {
                case BEFORE_COMMIT -> "TX_EVENT_BEFORE_COMMIT";
                case AFTER_COMMIT -> "TX_EVENT_AFTER_COMMIT";
                case AFTER_ROLLBACK -> "TX_EVENT_AFTER_ROLLBACK";
                case AFTER_COMPLETION -> "TX_EVENT_AFTER_COMPLETION";
            };
        }

        private void markSkipped(Phase phaseToSkip) {
            for (Delivery delivery : deliveries) {
                if (delivery.listener.phase == phaseToSkip && "waiting".equals(delivery.status)) {
                    delivery.status = "skipped";
                }
            }
        }

        private Object state() {
            Map<String, Object> s = baseState();
            s.put("phase", phase);
            s.put("currentMethod", methodName);
            s.put("completion", completion);
            s.put("staged", rowState(staged));
            s.put("publishedEvents", eventState(events));
            s.put("deliveries", deliveryState(deliveries));
            if (failure != null) {
                s.put("failure", failureState(failure));
            }
            return s;
        }

        private void ensureOpen() {
            if (completed) {
                throw new IllegalStateException("Transaction already completed");
            }
        }
    }

    private static final class Listener {
        final String name;
        final Phase phase;
        final boolean fallbackExecution;
        int invocations;
        boolean failed;

        Listener(String name, Phase phase, boolean fallbackExecution) {
            this.name = name;
            this.phase = phase;
            this.fallbackExecution = fallbackExecution;
        }
    }

    private static final class DomainEvent {
        final String name;
        String status;

        DomainEvent(String name, String status) {
            this.name = name;
            this.status = status;
        }
    }

    private static final class Delivery {
        final DomainEvent event;
        final Listener listener;
        final String id;
        String status = "waiting";

        Delivery(DomainEvent event, Listener listener) {
            this.event = event;
            this.listener = listener;
            this.id = listener.name + ":" + event.name + ":" + listener.phase;
        }
    }

    private record Row(String id, String value) {
    }

    private record Failure(String listenerName, Phase phase, String exceptionName, String effect) {
    }
}
