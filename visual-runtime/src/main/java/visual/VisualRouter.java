package visual;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A <em>teaching model</em> of the single AMQP 0-9-1 step the "binding key vs
 * routing key" question is about: how one exchange decides which queues a
 * message belongs in. It is not a client library — it exists to make the two
 * keys, and the comparison between them, visible one binding at a time.
 *
 * <ul>
 *   <li>the <strong>routing key</strong> is a property of the <em>message</em>.
 *       The producer stamps it on every publish and names no queue;</li>
 *   <li>the <strong>binding key</strong> is a property of a <em>binding</em>, the
 *       link between an exchange and a queue. Whoever owns the queue declares
 *       it, once, at setup time;</li>
 *   <li>the exchange compares the two, and the <strong>exchange type</strong> is
 *       the comparison rule: {@code direct} = exact equality, {@code topic} =
 *       the binding key is a pattern ({@code *} one word, {@code #} zero or more
 *       words), {@code fanout} = both keys ignored;</li>
 *   <li>wildcards only mean anything on the <em>binding</em> side. A {@code *}
 *       inside a routing key is an ordinary character;</li>
 *   <li>the relation is many-to-many: several queues may share one binding key,
 *       and one queue may have several binding keys. Two matching bindings to the
 *       same queue still produce exactly one copy;</li>
 *   <li>a message that matches nothing is <strong>unroutable</strong> and is
 *       dropped silently — the publish call still succeeds.</li>
 * </ul>
 *
 * <p>Every step emits a {@link Trace} event with a bilingual description so the
 * UI can replay the comparison without re-running the code. The model is
 * intentionally dependency-free and holds routed copies in plain lists: there
 * are no consumers, acks or prefetch here — that is
 * {@code VisualBroker}'s job.
 */
public class VisualRouter {

    /** How the nameless default exchange is shown in the UI. */
    private static final String DEFAULT_EXCHANGE_NAME = "(AMQP default)";

    private final String exchange;
    /** direct | fanout | topic */
    private final String type;
    /** True for the nameless default exchange, where every queue is auto-bound by its own name. */
    private final boolean nameless;

    /** Bindings in declaration order — the exchange tries them in this order. */
    private final List<Binding> bindings = new ArrayList<>();

    /** Declared queues and the copies routed into them, in declaration order. */
    private final Map<String, List<Msg>> queues = new LinkedHashMap<>();

    private int published;
    private int copies;
    private int unroutable;

    /** The message currently being routed (null on topology events). */
    private Msg inFlight;
    /** published | matching | routed | unroutable */
    private String stage = "";

    /**
     * Declares an exchange.
     *
     * @param type {@code direct} (binding key must equal the routing key),
     *             {@code topic} (the binding key is a {@code *} / {@code #}
     *             pattern) or {@code fanout} (both keys ignored)
     */
    public VisualRouter(String exchange, String type) {
        this(exchange, type, false);
    }

    private VisualRouter(String exchange, String type, boolean nameless) {
        if (!type.equals("direct") && !type.equals("fanout") && !type.equals("topic")) {
            throw new IllegalArgumentException("unknown exchange type '" + type
                    + "' — use direct, fanout or topic");
        }
        this.exchange = exchange;
        this.type = type;
        this.nameless = nameless;
        Trace.event("EXCHANGE_DECLARED",
                nameless
                        ? "The nameless default exchange: a direct exchange every queue is bound to automatically"
                        : "Declared a " + type + " exchange '" + exchange
                                + "' — its type is the rule used to compare a binding key with a routing key",
                nameless
                        ? "Безымянный default exchange: direct-exchange, к которому каждая очередь привязывается автоматически"
                        : "Объявлен exchange '" + exchange + "' типа " + type
                                + " — его тип и есть правило сравнения binding key с routing key",
                List.of("exchange"), state());
    }

    /**
     * The nameless default exchange ({@code ""}): a direct exchange to which the
     * broker binds every queue automatically with a binding key equal to the
     * queue name. Publishing "straight to a queue" is really this.
     */
    public static VisualRouter defaultExchange() {
        return new VisualRouter(DEFAULT_EXCHANGE_NAME, "direct", true);
    }

    // --- topology ----------------------------------------------------------

    /**
     * Declares a queue. On a normal exchange the queue starts with no binding, so
     * it receives nothing until one is added; on the default exchange the broker
     * immediately binds it with a binding key equal to its name.
     */
    public void declareQueue(String name) {
        if (queues.containsKey(name)) {
            throw new IllegalArgumentException("queue '" + name + "' is already declared");
        }
        queues.put(name, new ArrayList<>());
        clearVerdicts();
        Trace.event("QUEUE_DECLARED",
                "Declared queue '" + name
                        + "' — a queue with no binding receives nothing, whatever the routing key says",
                "Объявлена очередь '" + name
                        + "' — очередь без привязки не получает ничего, какой бы ни был routing key",
                List.of("queue:" + name), state());
        if (nameless) {
            addBinding(name, name, true);
        }
    }

    /**
     * Binds a queue to the exchange with a binding key: the rule that says "a
     * message published here whose routing key matches this belongs in that
     * queue". The producer never sees it.
     */
    public void bind(String queue, String bindingKey) {
        if (nameless) {
            throw new IllegalStateException("the default exchange cannot be bound by hand — "
                    + "every queue is auto-bound with its own name, so just declareQueue(...)");
        }
        queues.computeIfAbsent(queue, k -> new ArrayList<>());
        addBinding(queue, bindingKey, false);
    }

    /** Binds a queue with an empty binding key — the usual form on a fanout exchange. */
    public void bind(String queue) {
        bind(queue, "");
    }

    private void addBinding(String queue, String bindingKey, boolean implicit) {
        bindings.add(new Binding(queue, bindingKey, implicit));
        clearVerdicts();
        inFlight = null;
        String descEn;
        String descRu;
        if (implicit) {
            descEn = "The broker binds '" + queue + "' to the default exchange itself, with binding key '"
                    + queue + "' — that is why publishing with routing key '" + queue
                    + "' looks like publishing straight to the queue";
            descRu = "Брокер сам привязывает '" + queue + "' к default exchange с binding key '"
                    + queue + "' — поэтому публикация с routing key '" + queue
                    + "' выглядит как публикация прямо в очередь";
        } else if (bindingKey.isEmpty()) {
            descEn = "Bound '" + queue + "' with no binding key — a " + type
                    + " exchange ignores keys, so the binding alone is enough";
            descRu = "Очередь '" + queue + "' привязана без binding key — exchange типа " + type
                    + " игнорирует ключи, поэтому достаточно самой привязки";
        } else if (type.equals("fanout")) {
            descEn = "Bound '" + queue + "' with binding key '" + bindingKey
                    + "' — a fanout exchange never reads it, so the key is dead weight here";
            descRu = "Очередь '" + queue + "' привязана с binding key '" + bindingKey
                    + "' — fanout exchange его никогда не читает, так что здесь этот ключ — мёртвый груз";
        } else {
            descEn = "Bound '" + queue + "' with binding key '" + bindingKey
                    + "' — the binding key lives on the binding, and the queue's owner picks it, not the producer";
            descRu = "Очередь '" + queue + "' привязана с binding key '" + bindingKey
                    + "' — binding key живёт на привязке, и выбирает его владелец очереди, а не продюсер";
        }
        Trace.event("BINDING_ADDED", descEn, descRu,
                List.of("binding:" + (bindings.size() - 1), "queue:" + queue), state());
    }

    // --- routing -----------------------------------------------------------

    /**
     * Publishes one message. The exchange walks its bindings in order, compares
     * each binding key with this routing key using its type's rule, and stores a
     * copy in every queue that matched.
     *
     * @param routingKey the key the producer stamps on the message
     * @param messageId  a short id so the copies stay identifiable
     */
    public void publish(String routingKey, String messageId) {
        published++;
        inFlight = new Msg(messageId, routingKey);
        stage = "published";
        clearVerdicts();
        Trace.event("MESSAGE_PUBLISHED",
                "Producer publishes '" + messageId + "' with routing key '" + show(routingKey)
                        + "' — the routing key belongs to the message, and the producer names no queue",
                "Продюсер публикует '" + messageId + "' с routing key '" + show(routingKey)
                        + "' — routing key принадлежит сообщению, и никакой очереди продюсер не называет",
                List.of("exchange", "message"), state());

        if (routingKey.contains("*") || routingKey.contains("#")) {
            Trace.event("WILDCARD_IN_ROUTING_KEY",
                    "The routing key '" + routingKey + "' contains '*' or '#', but only a binding key may be a "
                            + "pattern — here they are ordinary characters and must be matched literally",
                    "Routing key '" + routingKey + "' содержит '*' или '#', но шаблоном может быть только "
                            + "binding key — здесь это обычные символы, и совпадать они должны буквально",
                    List.of("message"), state());
        }
        if (type.equals("fanout")) {
            Trace.event("KEYS_IGNORED",
                    "'" + exchange + "' is a fanout exchange: it reads neither the routing key nor any binding key, "
                            + "so every bound queue matches",
                    "'" + exchange + "' — fanout exchange: он не смотрит ни на routing key, ни на binding key, "
                            + "поэтому подходит каждая привязанная очередь",
                    List.of("exchange", "message"), state());
        }

        stage = "matching";
        List<String> matched = new ArrayList<>();
        for (int i = 0; i < bindings.size(); i++) {
            Binding b = bindings.get(i);
            List<String> highlight = List.of("binding:" + i, "queue:" + b.queue, "message");
            if (!matches(b.key, routingKey)) {
                b.verdict = "no-match";
                Trace.event("BINDING_SKIPPED",
                        "No match: " + reasonEn(b.key, routingKey, false) + ", so '" + b.queue
                                + "' gets nothing from this message",
                        "Не совпало: " + reasonRu(b.key, routingKey, false) + ", поэтому '" + b.queue
                                + "' ничего от этого сообщения не получит",
                        highlight, state());
            } else if (matched.contains(b.queue)) {
                b.verdict = "duplicate";
                Trace.event("BINDING_DUPLICATE",
                        "A second binding of '" + b.queue + "' also matches (" + reasonEn(b.key, routingKey, true)
                                + "), but a queue never gets two copies of one message",
                        "Вторая привязка очереди '" + b.queue + "' тоже совпала (" + reasonRu(b.key, routingKey, true)
                                + "), но двух копий одного сообщения очередь не получит",
                        highlight, state());
            } else {
                b.verdict = "match";
                matched.add(b.queue);
                Trace.event("BINDING_MATCHED",
                        "Match: " + reasonEn(b.key, routingKey, true) + ", so '" + b.queue + "' is on the list",
                        "Совпало: " + reasonRu(b.key, routingKey, true) + ", поэтому '" + b.queue + "' в списке",
                        highlight, state());
            }
        }

        if (matched.isEmpty()) {
            unroutable++;
            stage = "unroutable";
            Trace.event("MESSAGE_UNROUTABLE",
                    "No binding key on '" + exchange + "' matches routing key '" + show(routingKey)
                            + "' — the message is unroutable and is dropped silently, yet the publish call succeeded",
                    "Ни один binding key на '" + exchange + "' не подошёл под routing key '" + show(routingKey)
                            + "' — сообщение недоставляемо и молча выброшено, хотя вызов publish отработал успешно",
                    List.of("exchange", "message"), state());
            return;
        }

        List<String> highlight = new ArrayList<>();
        highlight.add("message");
        for (String queue : matched) {
            queues.get(queue).add(new Msg(messageId, routingKey));
            copies++;
            highlight.add("queue:" + queue);
        }
        stage = "routed";
        Trace.event("MESSAGE_ROUTED",
                "'" + messageId + "' is stored in " + matched.size() + " queue(s): " + String.join(", ", matched)
                        + " — one copy each, and the routing key travels with every copy",
                "'" + messageId + "' сохранено в очередях (" + matched.size() + "): " + String.join(", ", matched)
                        + " — по одной копии, и routing key едет вместе с каждой копией",
                highlight, state());
    }

    // --- matching rules ----------------------------------------------------

    /** Applies this exchange type's comparison rule to one binding key. */
    private boolean matches(String bindingKey, String routingKey) {
        return switch (type) {
            case "fanout" -> true;
            case "topic" -> topicMatches(bindingKey, routingKey);
            default -> bindingKey.equals(routingKey);
        };
    }

    private String reasonEn(String bindingKey, String routingKey, boolean hit) {
        return switch (type) {
            case "fanout" -> "fanout ignores both keys";
            case "topic" -> "pattern '" + show(bindingKey) + "' does " + (hit ? "" : "not ")
                    + "match '" + show(routingKey) + "' ('*' is exactly one word, '#' is zero or more)";
            default -> "'" + show(bindingKey) + "' " + (hit ? "==" : "!=") + " '" + show(routingKey)
                    + "' (direct compares the whole key, character for character)";
        };
    }

    private String reasonRu(String bindingKey, String routingKey, boolean hit) {
        return switch (type) {
            case "fanout" -> "fanout игнорирует оба ключа";
            case "topic" -> "шаблон '" + show(bindingKey) + "' " + (hit ? "подходит под" : "не подходит под")
                    + " '" + show(routingKey) + "' ('*' — ровно одно слово, '#' — ноль или больше)";
            default -> "'" + show(bindingKey) + "' " + (hit ? "==" : "!=") + " '" + show(routingKey)
                    + "' (direct сравнивает ключ целиком, символ в символ)";
        };
    }

    /** AMQP topic matching: {@code *} is exactly one word, {@code #} is zero or more. */
    private static boolean topicMatches(String pattern, String routingKey) {
        return topicMatches(pattern.split("\\.", -1), 0, routingKey.split("\\.", -1), 0);
    }

    private static boolean topicMatches(String[] p, int i, String[] k, int j) {
        if (i == p.length) {
            return j == k.length;
        }
        if (p[i].equals("#")) {
            for (int n = j; n <= k.length; n++) {
                if (topicMatches(p, i + 1, k, n)) {
                    return true;
                }
            }
            return false;
        }
        if (j == k.length) {
            return false;
        }
        if (!p[i].equals("*") && !p[i].equals(k[j])) {
            return false;
        }
        return topicMatches(p, i + 1, k, j + 1);
    }

    // --- state snapshot ----------------------------------------------------

    private void clearVerdicts() {
        for (Binding b : bindings) {
            b.verdict = "pending";
        }
    }

    /** Renders an empty key readably inside a description. */
    private static String show(String key) {
        return key.isEmpty() ? "(empty)" : key;
    }

    private Object state() {
        Map<String, Object> s = new LinkedHashMap<>();

        Map<String, Object> ex = new LinkedHashMap<>();
        ex.put("name", exchange);
        ex.put("type", type);
        ex.put("isDefault", nameless);
        s.put("exchange", ex);

        List<Object> bs = new ArrayList<>();
        for (Binding b : bindings) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("queue", b.queue);
            m.put("key", b.key);
            m.put("verdict", b.verdict);
            m.put("implicit", b.implicit);
            bs.add(m);
        }
        s.put("bindings", bs);

        List<Object> qs = new ArrayList<>();
        for (Map.Entry<String, List<Msg>> e : queues.entrySet()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", e.getKey());
            List<Object> msgs = new ArrayList<>();
            for (Msg msg : e.getValue()) {
                Map<String, Object> mm = new LinkedHashMap<>();
                mm.put("id", msg.id);
                mm.put("routingKey", msg.routingKey);
                msgs.add(mm);
            }
            m.put("messages", msgs);
            qs.add(m);
        }
        s.put("queues", qs);

        if (inFlight != null) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", inFlight.id);
            m.put("routingKey", inFlight.routingKey);
            m.put("stage", stage);
            m.put("literalWildcard",
                    inFlight.routingKey.contains("*") || inFlight.routingKey.contains("#"));
            s.put("message", m);
        }

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("published", published);
        stats.put("copies", copies);
        stats.put("unroutable", unroutable);
        s.put("stats", stats);
        return s;
    }

    // --- inner types -------------------------------------------------------

    private static final class Binding {
        final String queue;
        final String key;
        /** True when the broker created this binding itself (default exchange). */
        final boolean implicit;
        /** pending | match | no-match | duplicate — the verdict for the current message. */
        String verdict = "pending";

        Binding(String queue, String key, boolean implicit) {
            this.queue = queue;
            this.key = key;
            this.implicit = implicit;
        }
    }

    private static final class Msg {
        final String id;
        final String routingKey;

        Msg(String id, String routingKey) {
            this.id = id;
            this.routingKey = routingKey;
        }
    }
}
