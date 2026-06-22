package visual;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A dependency-free teaching model of how Spring's {@code @Transactional} works
 * under the hood: through an AOP <em>proxy</em>. It is not Spring itself; it
 * shows the mental model interviewers ask for:
 *
 * <ul>
 *   <li>Spring wraps the bean in a proxy that intercepts incoming calls,</li>
 *   <li>when an external caller invokes a {@code @Transactional} method through
 *       that proxy, the proxy opens a transaction, runs the method, and commits
 *       on normal return or rolls back on a runtime exception,</li>
 *   <li>a self-invocation ({@code this.other()}) calls the target object
 *       directly and bypasses the proxy, so the inner {@code @Transactional} is
 *       silently ignored,</li>
 *   <li>calling a method on a hand-created object (not a Spring bean) has no
 *       proxy at all, so {@code @Transactional} does nothing.</li>
 * </ul>
 *
 * <p>Every step emits {@link Trace} events so a topic visualizer can replay the
 * proxy, the call stack and the database without bringing Spring onto the
 * learner classpath.
 */
public class VisualTxProxy {

    private final String bean;
    private final String proxy;
    private final Map<String, String> database = new LinkedHashMap<>();
    private final List<Row> staged = new ArrayList<>();
    private final Deque<Frame> stack = new ArrayDeque<>();
    private boolean txActive;
    private String txOwner;

    public VisualTxProxy() {
        this("OrderService");
    }

    public VisualTxProxy(String bean) {
        this.bean = bean;
        this.proxy = bean + "$$SpringProxy";
        Trace.event("TX_PROXY_CREATED",
                "Spring wraps bean '" + bean + "' in proxy '" + proxy + "'; injected references point at the proxy, not the raw object",
                "Spring оборачивает бин '" + bean + "' в proxy '" + proxy + "'; внедрённые ссылки указывают на proxy, а не на сам объект",
                List.of("proxy"), state());
    }

    /**
     * An external caller invokes a method through the injected proxy reference.
     * The proxy intercepts the call and, if the method is {@code @Transactional}
     * and no transaction is active yet, opens one before the body runs.
     */
    public VisualTxProxy proxyCall(String method, boolean transactional) {
        Trace.event("TX_PROXY_INTERCEPT",
                "Proxy intercepts external call to '" + method + "'" + annotationNote(transactional),
                "Proxy перехватывает внешний вызов '" + method + "'" + annotationNoteRu(transactional),
                List.of("proxy", "call:" + method), state());
        boolean inTx = txActive;
        if (transactional && !txActive) {
            txActive = true;
            txOwner = method;
            inTx = true;
            stack.push(new Frame(method, "proxy", transactional, true));
            Trace.event("TX_BEGIN",
                    "Proxy opens a transaction before '" + method + "' runs (PROPAGATION_REQUIRED)",
                    "Proxy открывает транзакцию перед запуском '" + method + "' (PROPAGATION_REQUIRED)",
                    List.of("tx", "call:" + method), state());
            return this;
        }
        stack.push(new Frame(method, "proxy", transactional, inTx));
        return this;
    }

    /**
     * The currently running method calls {@code this.method()} on its own object.
     * The call goes straight to the target instance, so the proxy never sees it
     * and any {@code @Transactional} on that method is ignored.
     */
    public VisualTxProxy selfInvoke(String method, boolean transactional) {
        boolean inTx = txActive;
        stack.push(new Frame(method, "self", transactional, inTx));
        Trace.event("TX_SELF_INVOCATION",
                "Internal call this." + method + "() bypasses the proxy; its @Transactional is not applied",
                "Внутренний вызов this." + method + "() обходит proxy; его @Transactional не применяется",
                List.of("call:" + method, "bean"), state());
        if (transactional && !txActive) {
            Trace.event("TX_NO_TRANSACTION",
                    "'" + method + "' is @Transactional but runs with no transaction, because the proxy was bypassed",
                    "'" + method + "' помечен @Transactional, но выполняется без транзакции, потому что proxy был обойдён",
                    List.of("call:" + method), state());
        }
        return this;
    }

    /**
     * A method is invoked on a hand-created object (e.g. {@code new OrderService()})
     * instead of the Spring-managed bean. There is no proxy at all, so
     * {@code @Transactional} has no effect.
     */
    public VisualTxProxy directCall(String method, boolean transactional) {
        stack.push(new Frame(method, "direct", transactional, false));
        Trace.event("TX_DIRECT_CALL",
                "'" + method + "' is called on a raw object that is not a Spring bean; there is no proxy to intercept it",
                "'" + method + "' вызывается на «голом» объекте, который не является бином Spring; перехватывать его некому",
                List.of("call:" + method), state());
        if (transactional) {
            Trace.event("TX_NO_TRANSACTION",
                    "'" + method + "' is @Transactional but runs with no transaction, because there is no proxy",
                    "'" + method + "' помечен @Transactional, но выполняется без транзакции, потому что нет proxy",
                    List.of("call:" + method), state());
        }
        return this;
    }

    /**
     * The current method writes a row. Inside a transaction the row is staged
     * and only becomes durable on commit; with no transaction the write
     * autocommits immediately and is outside any rollback boundary.
     */
    public VisualTxProxy persist(String id, String value) {
        if (txActive) {
            staged.add(new Row(id, value));
            Trace.event("TX_PERSIST",
                    "persist(" + id + ") stages a row inside the transaction; it commits or rolls back as a unit",
                    "persist(" + id + ") помещает строку внутрь транзакции; она зафиксируется или откатится целиком",
                    List.of("staged:" + id), state());
        } else {
            database.put(id, value);
            Trace.event("TX_PERSIST",
                    "persist(" + id + ") runs with no transaction, so the write autocommits immediately and cannot be rolled back",
                    "persist(" + id + ") выполняется без транзакции, поэтому запись фиксируется сразу и не может быть откачена",
                    List.of("db:" + id), state());
        }
        return this;
    }

    /**
     * The whole call chain returns normally. If a transaction was opened by the
     * proxy, the proxy commits it as the outermost transactional frame unwinds.
     */
    public VisualTxProxy returnNormally() {
        if (txActive) {
            for (Row row : staged) {
                database.put(row.id(), row.value());
            }
            int committed = staged.size();
            staged.clear();
            String owner = txOwner;
            txActive = false;
            txOwner = null;
            stack.clear();
            Trace.event("TX_COMMIT",
                    "'" + owner + "' returns normally, so the proxy commits the transaction; " + committed + " staged row(s) become durable",
                    "'" + owner + "' завершается нормально, поэтому proxy фиксирует транзакцию; " + committed + " подготовленных строк(и) становятся сохранёнными",
                    List.of("tx", "db"), state());
        } else {
            stack.clear();
            Trace.event("TX_RETURN",
                    "Call chain returns; there was no transaction to commit",
                    "Цепочка вызовов завершается; транзакции, которую нужно зафиксировать, не было",
                    List.of(), state());
        }
        return this;
    }

    /**
     * The current method exits by throwing an unchecked exception. If a
     * transaction is active, it propagates out through the proxy, which rolls
     * the transaction back.
     */
    public VisualTxProxy throwRuntime(String exceptionName) {
        if (txActive) {
            List<String> ids = new ArrayList<>();
            for (Row row : staged) {
                ids.add(row.id());
            }
            staged.clear();
            String owner = txOwner;
            txActive = false;
            txOwner = null;
            stack.clear();
            Trace.event("TX_ROLLBACK",
                    exceptionName + " propagates out of '" + owner + "'; the proxy rolls the transaction back and discards staged rows " + ids,
                    exceptionName + " выходит из '" + owner + "'; proxy откатывает транзакцию и отбрасывает подготовленные строки " + ids,
                    List.of("tx"), state());
        } else {
            stack.clear();
            Trace.event("TX_RETURN",
                    exceptionName + " propagates out, but there was no transaction to roll back",
                    exceptionName + " выходит наружу, но транзакции, которую нужно откатить, не было",
                    List.of(), state());
        }
        return this;
    }

    private static String annotationNote(boolean transactional) {
        return transactional ? "; the method is @Transactional" : "; the method is not @Transactional";
    }

    private static String annotationNoteRu(boolean transactional) {
        return transactional ? "; метод помечен @Transactional" : "; метод не помечен @Transactional";
    }

    /** Builds the JSON-serializable snapshot consumed by the visualizer. */
    private Object state() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("bean", bean);
        s.put("proxy", proxy);
        s.put("txActive", txActive);
        s.put("txOwner", txOwner == null ? "" : txOwner);
        s.put("stack", stackState());
        s.put("staged", rowList(staged));
        s.put("database", databaseList());
        return s;
    }

    private List<Object> stackState() {
        // Render outermost frame first so the visualizer reads top-to-bottom.
        List<Frame> frames = new ArrayList<>(stack);
        List<Object> out = new ArrayList<>();
        for (int i = frames.size() - 1; i >= 0; i--) {
            Frame f = frames.get(i);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("method", f.method);
            m.put("via", f.via);
            m.put("transactional", f.transactional);
            m.put("inTx", f.inTx);
            out.add(m);
        }
        return out;
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

    private static List<Object> rowList(List<Row> rows) {
        List<Object> out = new ArrayList<>();
        for (Row row : rows) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("id", row.id());
            r.put("value", row.value());
            out.add(r);
        }
        return out;
    }

    private record Row(String id, String value) {
    }

    private static final class Frame {
        final String method;
        final String via;
        final boolean transactional;
        final boolean inTx;

        Frame(String method, String via, boolean transactional, boolean inTx) {
            this.method = method;
            this.via = via;
            this.transactional = transactional;
            this.inTx = inTx;
        }
    }
}
