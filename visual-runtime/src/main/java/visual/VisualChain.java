package visual;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A <em>teaching model</em> of the Chain of Responsibility pattern. It is not a
 * library — it reproduces exactly the idea an interviewer asks about: a request
 * travels along a linked chain of handlers, and each handler in turn either
 * handles it or passes it on to the next, so the sender is decoupled from
 * whichever receiver ends up doing the work.
 *
 * <p>The concrete chain modelled here is an <strong>approval / escalation
 * chain</strong>: every handler has a name and an approval {@code limit}. A
 * request carries an {@code amount}; a handler approves it when
 * {@code amount <= limit}, otherwise it escalates to the next handler. This is
 * the most common "first capable handler wins, then stop" form of the pattern.
 * If no handler can approve, the request falls off the end unhandled — which is
 * why a real chain usually ends with a default/tail handler.
 *
 * <p>Every step emits a {@link Trace} event with a bilingual description so the
 * UI can replay the request walking the chain without re-running the code. The
 * model is intentionally dependency-free.
 */
public class VisualChain {

    private final String name;
    private final List<Handler> handlers = new ArrayList<>();

    /** The request currently walking the chain (null between dispatches). */
    private Request current;

    public VisualChain() {
        this("chain");
    }

    public VisualChain(String name) {
        this.name = name;
        Trace.event("CHAIN_CREATED",
                "Created an empty chain '" + name + "' — no handlers yet",
                "Создана пустая цепочка '" + name + "' — обработчиков пока нет",
                List.of(), state());
    }

    /**
     * Appends a handler to the tail of the chain. The handler approves any
     * request whose amount is at or below {@code limit}.
     *
     * @return this chain, so calls can be fluently chained
     */
    public VisualChain addHandler(String handlerName, int limit) {
        Handler h = new Handler(handlers.size(), handlerName, limit);
        handlers.add(h);
        Trace.event("HANDLER_ADDED",
                "Linked handler '" + handlerName + "' (approves up to " + limit
                        + ") to the end of the chain",
                "В конец цепочки добавлен обработчик '" + handlerName
                        + "' (одобряет до " + limit + ")",
                List.of("handler:h" + h.index), state());
        return this;
    }

    /**
     * Sends a request into the head of the chain. Each handler in order inspects
     * it; the first one that can approve it does and the walk stops, otherwise
     * the request is passed on. Returns the approving handler's limit, or
     * {@code null} if the request reached the end unhandled.
     */
    public Integer handle(String label, int amount) {
        // Reset handler statuses from any previous dispatch.
        for (Handler h : handlers) {
            h.status = "idle";
        }
        current = new Request(label, amount);
        Trace.event("REQUEST_RECEIVED",
                "Request '" + label + "' (amount " + amount + ") enters the chain at the head",
                "Запрос '" + label + "' (сумма " + amount + ") входит в цепочку с начала",
                List.of("request"), state());

        for (Handler h : handlers) {
            h.status = "inspecting";
            current.position = h.index;
            Trace.event("HANDLER_INSPECT",
                    "'" + h.name + "' inspects '" + label + "': is " + amount + " <= " + h.limit + "?",
                    "'" + h.name + "' проверяет '" + label + "': " + amount + " <= " + h.limit + "?",
                    List.of("handler:h" + h.index, "request"), state());

            if (amount <= h.limit) {
                h.status = "handled";
                current.outcome = "handled";
                Trace.event("REQUEST_HANDLED",
                        "'" + h.name + "' approves '" + label + "' (" + amount + " <= " + h.limit
                                + ") — the chain stops here",
                        "'" + h.name + "' одобряет '" + label + "' (" + amount + " <= " + h.limit
                                + ") — цепочка останавливается здесь",
                        List.of("handler:h" + h.index, "request"), state());
                return h.limit;
            }

            h.status = "passed";
            Trace.event("HANDLER_PASS",
                    "'" + h.name + "' cannot approve " + amount + " (limit " + h.limit
                            + ") — passes it to the next handler",
                    "'" + h.name + "' не может одобрить " + amount + " (лимит " + h.limit
                            + ") — передаёт дальше следующему обработчику",
                    List.of("handler:h" + h.index, "request"), state());
        }

        current.outcome = "unhandled";
        current.position = handlers.size();
        Trace.event("REQUEST_UNHANDLED",
                "No handler could approve '" + label + "' — it falls off the end of the chain "
                        + "(a real chain needs a default/tail handler)",
                "Ни один обработчик не смог одобрить '" + label + "' — запрос выпадает из конца "
                        + "цепочки (настоящей цепочке нужен обработчик по умолчанию)",
                List.of("request"), state());
        return null;
    }

    /** Builds the JSON-serializable snapshot consumed by the visualizer. */
    private Object state() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("name", name);

        List<Object> hs = new ArrayList<>();
        for (Handler h : handlers) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", "h" + h.index);
            m.put("name", h.name);
            m.put("limit", h.limit);
            m.put("status", h.status);
            hs.add(m);
        }
        s.put("handlers", hs);

        if (current != null) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("label", current.label);
            r.put("amount", current.amount);
            r.put("position", current.position);
            r.put("outcome", current.outcome);
            s.put("request", r);
        }
        return s;
    }

    private static final class Handler {
        final int index;
        final String name;
        final int limit;
        /** idle | inspecting | passed | handled */
        String status = "idle";

        Handler(int index, String name, int limit) {
            this.index = index;
            this.name = name;
            this.limit = limit;
        }
    }

    private static final class Request {
        final String label;
        final int amount;
        /** Index of the handler currently holding the request (handlers.size() if it fell off). */
        int position = -1;
        /** pending | handled | unhandled */
        String outcome = "pending";

        Request(String label, int amount) {
            this.label = label;
            this.amount = amount;
        }
    }
}
