package visual;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A dependency-free teaching model of the Spring "self-invocation" problem with
 * {@code @Transactional}. It is not Spring: it does not run a real transaction
 * manager or parse annotations. It keeps the core interview mental model small:
 *
 * <ul>
 *   <li>Spring wraps a bean in a proxy; the transaction interceptor lives on the
 *       proxy, not on the target object,</li>
 *   <li>a call through the proxy reference lets the interceptor open/commit a
 *       transaction,</li>
 *   <li>an internal {@code this.method()} call goes straight to the target and
 *       skips the proxy, so the inner {@code @Transactional} is ignored,</li>
 *   <li>calling through an injected self/proxy reference (or moving the method to
 *       another bean) re-enters the proxy and restores the transaction.</li>
 * </ul>
 *
 * <p>Propagation is modelled at a high level: {@code REQUIRED} joins an active
 * transaction, {@code REQUIRES_NEW} always starts its own. A self-invoked method
 * keeps neither behaviour because the interceptor never sees the call.
 */
public class VisualSelfInvocation {

    private final String bean;
    private final String proxy;
    private final Map<String, Method> registered = new LinkedHashMap<>();
    private final List<Frame> stack = new ArrayList<>();
    private final List<Transaction> transactions = new ArrayList<>();
    private int txCounter;

    public VisualSelfInvocation() {
        this("OrderService");
    }

    public VisualSelfInvocation(String bean) {
        this.bean = bean;
        this.proxy = bean + "$$SpringCGLIB";
        Trace.event("SI_PROXY_CREATED",
                "Spring wraps bean '" + bean + "' in proxy '" + proxy
                        + "'; the transaction interceptor lives on the proxy, not on the target",
                "Spring оборачивает bean '" + bean + "' в proxy '" + proxy
                        + "'; transaction interceptor находится на proxy, а не на target",
                List.of("proxy", "bean"), state());
    }

    /**
     * Registers a plain method (no {@code @Transactional}).
     */
    public VisualSelfInvocation method(String name) {
        return register(name, false, "");
    }

    /**
     * Registers a {@code @Transactional} method with the given propagation
     * (e.g. {@code REQUIRED} or {@code REQUIRES_NEW}).
     */
    public VisualSelfInvocation transactional(String name, String propagation) {
        return register(name, true, propagation);
    }

    private VisualSelfInvocation register(String name, boolean tx, String propagation) {
        registered.put(name, new Method(name, tx, propagation));
        Trace.event("SI_METHOD_REGISTERED",
                tx ? "Method '" + name + "' is @Transactional(propagation=" + propagation + ")"
                        : "Method '" + name + "' has no @Transactional",
                tx ? "Метод '" + name + "' помечен @Transactional(propagation=" + propagation + ")"
                        : "У метода '" + name + "' нет @Transactional",
                List.of("method:" + name), state());
        return this;
    }

    /**
     * A client calls {@code method} through the injected proxy reference. The
     * interceptor runs, so {@code @Transactional} takes effect.
     */
    public VisualSelfInvocation externalCall(String method) {
        Method m = methodOf(method);
        Frame frame = new Frame(m, "proxy");
        stack.add(frame);
        Trace.event("SI_EXTERNAL_CALL",
                "Client calls '" + method + "' through proxy '" + proxy
                        + "'; the call passes through the transaction interceptor",
                "Клиент вызывает '" + method + "' через proxy '" + proxy
                        + "'; вызов проходит через transaction interceptor",
                List.of("proxy", "method:" + method), state());
        applyTransaction(frame, true);
        return this;
    }

    /**
     * From inside the current method, an internal {@code this.method()} call is
     * made. It goes straight to the target object and skips the proxy, so the
     * interceptor never runs.
     */
    public VisualSelfInvocation selfInvoke(String method) {
        Method m = methodOf(method);
        Frame frame = new Frame(m, "this");
        stack.add(frame);
        Trace.event("SI_SELF_INVOKE",
                "Internal call this." + method + "() runs directly on the target bean; it does NOT pass through proxy '"
                        + proxy + "'",
                "Внутренний вызов this." + method + "() выполняется прямо на target bean; он НЕ проходит через proxy '"
                        + proxy + "'",
                List.of("bean", "method:" + method), state());
        applyTransaction(frame, false);
        return this;
    }

    /**
     * The fix: call {@code method} through an injected self/proxy reference (or a
     * different bean). The call re-enters the proxy, so the interceptor runs again.
     */
    public VisualSelfInvocation proxyInvoke(String method) {
        Method m = methodOf(method);
        Frame frame = new Frame(m, "injected");
        stack.add(frame);
        Trace.event("SI_PROXY_REENTER",
                "Call to '" + method + "' goes through the injected self/proxy reference; it re-enters proxy '"
                        + proxy + "', so the interceptor runs again",
                "Вызов '" + method + "' идёт через инжектированную ссылку на self/proxy; он снова входит в proxy '"
                        + proxy + "', поэтому interceptor выполняется",
                List.of("proxy", "method:" + method), state());
        applyTransaction(frame, true);
        return this;
    }

    /**
     * Runs one business step inside the current method, attributed to whatever
     * transaction (if any) is currently in effect.
     */
    public VisualSelfInvocation work(String action) {
        Frame top = top();
        if (top == null) {
            return this;
        }
        String txId = top.txId != null ? top.txId : activeTxId();
        top.lastAction = action;
        if (txId != null) {
            Trace.event("SI_WORK",
                    "'" + top.method.name + "' runs " + action + " inside transaction " + txId,
                    "'" + top.method.name + "' выполняет " + action + " внутри транзакции " + txId,
                    List.of("method:" + top.method.name, "tx:" + txId), state());
        } else {
            Trace.event("SI_WORK",
                    "'" + top.method.name + "' runs " + action + " with NO transaction (no commit/rollback boundary)",
                    "'" + top.method.name + "' выполняет " + action + " БЕЗ транзакции (нет границы commit/rollback)",
                    List.of("method:" + top.method.name), state());
        }
        return this;
    }

    /**
     * Returns from the current method. If it owns a transaction, that transaction
     * commits.
     */
    public VisualSelfInvocation ret() {
        Frame top = top();
        if (top == null) {
            return this;
        }
        stack.remove(stack.size() - 1);
        if (top.ownsTx) {
            transactionById(top.txId).status = "committed";
            Trace.event("SI_TX_COMMIT",
                    "Transaction " + top.txId + " owned by '" + top.method.name + "' commits",
                    "Транзакция " + top.txId + ", которой владеет '" + top.method.name + "', коммитится",
                    List.of("method:" + top.method.name, "tx:" + top.txId), state());
        }
        Trace.event("SI_RETURN",
                "'" + top.method.name + "' returns to the caller",
                "'" + top.method.name + "' возвращает управление вызывающему",
                List.of("method:" + top.method.name), state());
        return this;
    }

    private void applyTransaction(Frame frame, boolean throughProxy) {
        if (throughProxy) {
            if (frame.method.transactional) {
                String active = activeTxId();
                if (active == null || "REQUIRES_NEW".equals(frame.method.propagation)) {
                    beginTransaction(frame);
                } else {
                    joinTransaction(frame, active);
                }
            } else {
                frame.txId = activeTxId();
            }
        } else {
            // Self-invocation: the interceptor never sees this call.
            if (frame.method.transactional) {
                frame.bypassed = true;
                String active = activeTxId();
                if (active != null) {
                    frame.txId = active;
                    Trace.event("SI_TX_BYPASSED",
                            "@Transactional(propagation=" + frame.method.propagation + ") on '" + frame.method.name
                                    + "' is IGNORED: no new boundary is created, it just runs inside the caller's transaction "
                                    + active,
                            "@Transactional(propagation=" + frame.method.propagation + ") у '" + frame.method.name
                                    + "' ИГНОРИРУЕТСЯ: новая граница не создаётся, метод просто выполняется внутри транзакции вызывающего "
                                    + active,
                            List.of("method:" + frame.method.name, "tx:" + active), state());
                } else {
                    frame.txId = null;
                    Trace.event("SI_TX_BYPASSED",
                            "@Transactional on '" + frame.method.name
                                    + "' is IGNORED: no transaction is opened, the method runs with NO transaction at all",
                            "@Transactional у '" + frame.method.name
                                    + "' ИГНОРИРУЕТСЯ: транзакция не открывается, метод выполняется СОВСЕМ без транзакции",
                            List.of("method:" + frame.method.name), state());
                }
            } else {
                frame.txId = activeTxId();
            }
        }
    }

    private void beginTransaction(Frame frame) {
        String id = "tx-" + (++txCounter);
        transactions.add(new Transaction(id, frame.method.name, frame.method.propagation));
        frame.ownsTx = true;
        frame.txId = id;
        Trace.event("SI_TX_BEGIN",
                "Interceptor opens transaction " + id + " for '" + frame.method.name
                        + "' (propagation=" + frame.method.propagation + ")",
                "Interceptor открывает транзакцию " + id + " для '" + frame.method.name
                        + "' (propagation=" + frame.method.propagation + ")",
                List.of("method:" + frame.method.name, "tx:" + id), state());
    }

    private void joinTransaction(Frame frame, String active) {
        frame.txId = active;
        Trace.event("SI_TX_JOIN",
                "'" + frame.method.name + "' (propagation=REQUIRED) joins the existing transaction " + active,
                "'" + frame.method.name + "' (propagation=REQUIRED) присоединяется к существующей транзакции " + active,
                List.of("method:" + frame.method.name, "tx:" + active), state());
    }

    private Frame top() {
        return stack.isEmpty() ? null : stack.get(stack.size() - 1);
    }

    private String activeTxId() {
        for (int i = stack.size() - 1; i >= 0; i--) {
            Frame f = stack.get(i);
            if (f.ownsTx) {
                return f.txId;
            }
        }
        return null;
    }

    private Method methodOf(String name) {
        Method m = registered.get(name);
        if (m == null) {
            m = new Method(name, false, "");
            registered.put(name, m);
        }
        return m;
    }

    private Transaction transactionById(String id) {
        for (Transaction t : transactions) {
            if (t.id.equals(id)) {
                return t;
            }
        }
        return null;
    }

    private Object state() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("bean", bean);
        s.put("proxy", proxy);
        s.put("activeTx", activeTxId());
        s.put("registered", registeredState());
        s.put("stack", stackState());
        s.put("transactions", transactionState());
        return s;
    }

    private List<Object> registeredState() {
        List<Object> out = new ArrayList<>();
        for (Method m : registered.values()) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("method", m.name);
            map.put("transactional", m.transactional);
            map.put("propagation", m.propagation);
            out.add(map);
        }
        return out;
    }

    private List<Object> stackState() {
        List<Object> out = new ArrayList<>();
        for (Frame f : stack) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("method", f.method.name);
            map.put("via", f.via);
            map.put("declaredTx", f.method.transactional);
            map.put("propagation", f.method.propagation);
            map.put("ownsTx", f.ownsTx);
            map.put("bypassed", f.bypassed);
            map.put("txId", f.txId);
            map.put("lastAction", f.lastAction);
            out.add(map);
        }
        return out;
    }

    private List<Object> transactionState() {
        List<Object> out = new ArrayList<>();
        for (Transaction t : transactions) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", t.id);
            map.put("owner", t.owner);
            map.put("propagation", t.propagation);
            map.put("status", t.status);
            out.add(map);
        }
        return out;
    }

    private static final class Method {
        final String name;
        final boolean transactional;
        final String propagation;

        Method(String name, boolean transactional, String propagation) {
            this.name = name;
            this.transactional = transactional;
            this.propagation = propagation;
        }
    }

    private static final class Frame {
        final Method method;
        final String via;
        boolean ownsTx;
        boolean bypassed;
        String txId;
        String lastAction = "";

        Frame(Method method, String via) {
            this.method = method;
            this.via = via;
        }
    }

    private static final class Transaction {
        final String id;
        final String owner;
        final String propagation;
        String status = "active";

        Transaction(String id, String owner, String propagation) {
            this.id = id;
            this.owner = owner;
            this.propagation = propagation;
        }
    }
}
