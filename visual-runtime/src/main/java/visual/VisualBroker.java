package visual;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A <em>teaching model</em> of a message broker in the RabbitMQ (AMQP 0-9-1)
 * style. It is not a client library — it reproduces exactly the mechanics an
 * interviewer asks about when they say "how do queues like RabbitMQ work?":
 *
 * <ul>
 *   <li>a producer publishes to an <strong>exchange</strong>, never straight to a
 *       queue;</li>
 *   <li><strong>bindings</strong> plus the exchange type decide which queues get a
 *       copy — {@code direct} matches the routing key exactly, {@code fanout}
 *       copies to every bound queue, {@code topic} matches {@code *} / {@code #}
 *       patterns. A message matching nothing is unroutable and is discarded;</li>
 *   <li>a queue is an ordered FIFO buffer that decouples the producer's speed
 *       from the consumer's speed;</li>
 *   <li>the broker <strong>pushes</strong> messages to subscribed consumers,
 *       limited by each consumer's <strong>prefetch</strong> (its budget of
 *       unacknowledged messages) and shared round-robin between competing
 *       consumers on the same queue;</li>
 *   <li>a delivered message stays in an <em>unacked</em> state and is only
 *       deleted once the consumer <strong>acks</strong> it. A nack either
 *       requeues it or sends it to a <strong>dead-letter</strong> queue, and a
 *       consumer that dies before acking gets its messages requeued — which is
 *       why the practical guarantee is <em>at-least-once</em> delivery.</li>
 * </ul>
 *
 * <p>Every step emits a {@link Trace} event with a bilingual description so the
 * UI can replay the stream without re-running the code. The model is
 * intentionally dependency-free and single-threaded: "pushing" happens
 * synchronously inside {@code publish} / {@code ack}, which is what makes the
 * message flow readable step by step.
 */
public class VisualBroker {

    /** Declared exchanges, in declaration order. */
    private final Map<String, Exchange> exchanges = new LinkedHashMap<>();

    /** Declared queues, in declaration order. */
    private final Map<String, Queue> queues = new LinkedHashMap<>();

    /** Attached consumers, in subscription order. */
    private final List<Consumer> consumers = new ArrayList<>();

    /** Per-queue cursor that makes dispatch round-robin between competing consumers. */
    private final Map<String, Integer> roundRobin = new LinkedHashMap<>();

    private int published;
    private int delivered;
    private int acked;
    private int requeued;
    private int deadLettered;
    private int dropped;

    /** The message currently moving through the broker (null on topology events). */
    private Flight flight;

    public VisualBroker() {
        Trace.event("BROKER_CREATED",
                "Empty broker: no exchange, no queue, no consumer yet",
                "Пустой брокер: пока нет ни exchange, ни очереди, ни потребителя",
                List.of(), state());
    }

    // --- topology ----------------------------------------------------------

    /**
     * Declares an exchange. The producer publishes here; the exchange itself
     * stores nothing.
     *
     * @param type {@code direct} (exact routing key), {@code fanout} (every bound
     *             queue, routing key ignored) or {@code topic} ({@code *} = one
     *             word, {@code #} = zero or more words)
     */
    public void declareExchange(String name, String type) {
        if (!type.equals("direct") && !type.equals("fanout") && !type.equals("topic")) {
            throw new IllegalArgumentException("unknown exchange type '" + type
                    + "' — use direct, fanout or topic");
        }
        exchanges.put(name, new Exchange(name, type));
        flight = null;
        Trace.event("EXCHANGE_DECLARED",
                "Declared a " + type + " exchange '" + name
                        + "' — producers publish here, it stores nothing itself",
                "Объявлен exchange '" + name + "' типа " + type
                        + " — продюсеры публикуют сюда, сам он ничего не хранит",
                List.of("exchange:" + name), state());
    }

    /** Declares a queue with no dead-letter target. */
    public void declareQueue(String name) {
        declareQueue(name, "");
    }

    /**
     * Declares a queue.
     *
     * @param deadLetterQueue where messages rejected without requeue go; empty
     *                        means such messages are discarded forever
     */
    public void declareQueue(String name, String deadLetterQueue) {
        if (!deadLetterQueue.isEmpty() && !queues.containsKey(deadLetterQueue)) {
            throw new IllegalArgumentException("declare the dead-letter queue '"
                    + deadLetterQueue + "' before using it as a target");
        }
        queues.put(name, new Queue(name, deadLetterQueue));
        flight = null;
        String dlq = deadLetterQueue.isEmpty() ? "" : ", dead-letter target '" + deadLetterQueue + "'";
        String dlqRu = deadLetterQueue.isEmpty() ? "" : ", dead-letter очередь '" + deadLetterQueue + "'";
        Trace.event("QUEUE_DECLARED",
                "Declared queue '" + name + "'" + dlq + " — a FIFO buffer that actually stores messages",
                "Объявлена очередь '" + name + "'" + dlqRu + " — FIFO-буфер, который и хранит сообщения",
                List.of("queue:" + name), state());
    }

    /** Binds a queue to a fanout exchange (the routing key is ignored there). */
    public void bind(String exchange, String queue) {
        bind(exchange, queue, "");
    }

    /**
     * Binds a queue to an exchange with a binding key. The binding is the rule
     * that says "a message published here with this key belongs in that queue".
     */
    public void bind(String exchange, String queue, String bindingKey) {
        Exchange ex = requireExchange(exchange);
        requireQueue(queue);
        ex.bindings.add(new Binding(queue, bindingKey));
        flight = null;
        String key = bindingKey.isEmpty() ? "" : " with key '" + bindingKey + "'";
        String keyRu = bindingKey.isEmpty() ? "" : " с ключом '" + bindingKey + "'";
        Trace.event("QUEUE_BOUND",
                "Bound '" + queue + "' to exchange '" + exchange + "'" + key
                        + " — routing is decided by bindings, not by the producer",
                "Очередь '" + queue + "' привязана к exchange '" + exchange + "'" + keyRu
                        + " — маршрутизацию решают привязки, а не продюсер",
                List.of("exchange:" + exchange, "queue:" + queue), state());
    }

    /**
     * Attaches a consumer to a queue.
     *
     * @param prefetch how many messages the broker may leave unacknowledged with
     *                 this consumer at once (RabbitMQ's basic.qos)
     */
    public void subscribe(String consumer, String queue, int prefetch) {
        requireQueue(queue);
        if (prefetch < 1) {
            throw new IllegalArgumentException("prefetch must be at least 1");
        }
        consumers.add(new Consumer(consumer, queue, prefetch));
        flight = null;
        Trace.event("CONSUMER_ATTACHED",
                "Consumer '" + consumer + "' subscribes to '" + queue + "' with prefetch "
                        + prefetch + " — the broker pushes to it, it does not poll",
                "Потребитель '" + consumer + "' подписался на '" + queue + "' с prefetch "
                        + prefetch + " — брокер сам проталкивает ему сообщения, тот не опрашивает",
                List.of("queue:" + queue, "consumer:" + consumer), state());
        dispatch(queue);
    }

    // --- message flow ------------------------------------------------------

    /**
     * Publishes one message to an exchange. The exchange routes it to every queue
     * whose binding matches, then the broker pushes what it can to consumers.
     */
    public void publish(String exchange, String routingKey, String messageId, String payload) {
        Exchange ex = requireExchange(exchange);
        published++;
        flight = new Flight(messageId, payload, "published");
        flight.exchange = exchange;
        flight.routingKey = routingKey;
        Trace.event("MESSAGE_PUBLISHED",
                "Producer publishes '" + messageId + "' (" + payload + ") to exchange '"
                        + exchange + "' with routing key '" + routingKey + "'",
                "Продюсер публикует '" + messageId + "' (" + payload + ") в exchange '"
                        + exchange + "' с routing key '" + routingKey + "'",
                List.of("exchange:" + exchange, "flight"), state());

        List<String> matched = new ArrayList<>();
        for (Binding b : ex.bindings) {
            if (ex.matches(b.key, routingKey) && !matched.contains(b.queue)) {
                matched.add(b.queue);
            }
        }

        if (matched.isEmpty()) {
            dropped++;
            flight.stage = "unroutable";
            Trace.event("MESSAGE_UNROUTABLE",
                    "No binding on '" + exchange + "' matches key '" + routingKey
                            + "' — the message is unroutable and is silently discarded",
                    "Ни одна привязка exchange '" + exchange + "' не подходит под ключ '"
                            + routingKey + "' — сообщение недоставляемо и молча выбрасывается",
                    List.of("exchange:" + exchange, "flight"), state());
            return;
        }

        for (String queueName : matched) {
            queues.get(queueName).messages.add(new Msg(messageId, payload));
            flight.stage = "routed";
            flight.queue = queueName;
            Trace.event("MESSAGE_ROUTED",
                    "Binding matched: a copy of '" + messageId + "' is stored at the tail of queue '"
                            + queueName + "' (depth " + queues.get(queueName).messages.size() + ")",
                    "Привязка совпала: копия '" + messageId + "' сохранена в хвост очереди '"
                            + queueName + "' (глубина " + queues.get(queueName).messages.size() + ")",
                    List.of("queue:" + queueName, "msg:" + messageId, "flight"), state());
        }
        for (String queueName : matched) {
            dispatch(queueName);
        }
    }

    /**
     * Acknowledges a delivered message: only now does the broker forget it.
     * Freeing a prefetch slot lets the next queued message be pushed.
     */
    public void ack(String messageId) {
        Consumer c = ownerOf(messageId);
        Msg m = takeUnacked(c, messageId);
        acked++;
        flight = new Flight(m.id, m.payload, "acked");
        flight.queue = c.queue;
        flight.consumer = c.name;
        Trace.event("MESSAGE_ACKED",
                "'" + c.name + "' acks '" + m.id
                        + "' — only now is the message deleted from the broker, and a prefetch slot is free",
                "'" + c.name + "' подтверждает (ack) '" + m.id
                        + "' — только теперь брокер удаляет сообщение, и слот prefetch освобождается",
                List.of("consumer:" + c.name, "msg:" + m.id, "flight"), state());
        dispatch(c.queue);
    }

    /**
     * Rejects a delivered message.
     *
     * @param requeue {@code true} puts it back at the head of its queue (it will
     *                be redelivered — a failing handler can loop forever);
     *                {@code false} sends it to the queue's dead-letter target, or
     *                discards it when there is none
     */
    public void nack(String messageId, boolean requeue) {
        Consumer c = ownerOf(messageId);
        Msg m = takeUnacked(c, messageId);
        Queue q = queues.get(c.queue);

        if (requeue) {
            m.redelivered = true;
            q.messages.add(0, m);
            requeued++;
            flight = new Flight(m.id, m.payload, "requeued");
            flight.queue = q.name;
            flight.consumer = c.name;
            Trace.event("MESSAGE_REQUEUED",
                    "'" + c.name + "' nacks '" + m.id
                            + "' with requeue — it returns to the head of '" + q.name
                            + "' and will be redelivered (a poison message can spin here forever)",
                    "'" + c.name + "' отклоняет (nack) '" + m.id
                            + "' с requeue — сообщение возвращается в голову '" + q.name
                            + "' и будет доставлено снова (ядовитое сообщение может крутиться вечно)",
                    List.of("queue:" + q.name, "msg:" + m.id, "flight"), state());
            dispatch(q.name);
            return;
        }

        if (q.deadLetterQueue.isEmpty()) {
            dropped++;
            flight = new Flight(m.id, m.payload, "dropped");
            flight.queue = q.name;
            flight.consumer = c.name;
            Trace.event("MESSAGE_DROPPED",
                    "'" + c.name + "' rejects '" + m.id + "' without requeue, and '" + q.name
                            + "' has no dead-letter queue — the message is gone for good",
                    "'" + c.name + "' отклоняет '" + m.id + "' без requeue, а у '" + q.name
                            + "' нет dead-letter очереди — сообщение потеряно навсегда",
                    List.of("queue:" + q.name, "flight"), state());
            return;
        }

        queues.get(q.deadLetterQueue).messages.add(m);
        deadLettered++;
        flight = new Flight(m.id, m.payload, "dead-lettered");
        flight.queue = q.deadLetterQueue;
        flight.consumer = c.name;
        Trace.event("MESSAGE_DEAD_LETTERED",
                "'" + c.name + "' rejects '" + m.id + "' without requeue — it is dead-lettered to '"
                        + q.deadLetterQueue + "' for inspection instead of being retried forever",
                "'" + c.name + "' отклоняет '" + m.id + "' без requeue — сообщение уходит в '"
                        + q.deadLetterQueue + "' на разбор вместо бесконечных повторов",
                List.of("queue:" + q.deadLetterQueue, "msg:" + m.id, "flight"), state());
        dispatch(q.deadLetterQueue);
    }

    /**
     * Kills a consumer that has unacknowledged messages, e.g. the process was
     * restarted mid-handler. Everything it never acked goes back to the head of
     * its queue marked {@code redelivered} — this is why delivery is
     * at-least-once and consumers must be idempotent.
     */
    public void crashConsumer(String consumer) {
        Consumer c = requireConsumer(consumer);
        consumers.remove(c);
        Queue q = queues.get(c.queue);
        int lost = c.unacked.size();
        for (int i = lost - 1; i >= 0; i--) {
            Msg m = c.unacked.get(i);
            m.redelivered = true;
            q.messages.add(0, m);
            requeued++;
        }
        c.unacked.clear();
        flight = null;
        Trace.event("CONSUMER_CRASHED",
                "Consumer '" + consumer + "' died before acking: its " + lost
                        + " unacked message(s) return to the head of '" + q.name
                        + "' and will be delivered again — at-least-once, so the work may repeat",
                "Потребитель '" + consumer + "' умер, не отправив ack: его неподтверждённых сообщений — "
                        + lost + ", они возвращаются в голову '" + q.name
                        + "' и будут доставлены снова — at-least-once, поэтому работа может повториться",
                List.of("queue:" + q.name, "consumer:" + consumer), state());
        dispatch(c.queue);
    }

    // --- dispatch ----------------------------------------------------------

    /** Pushes as many messages as the consumers' prefetch budgets allow. */
    private void dispatch(String queueName) {
        Queue q = queues.get(queueName);
        List<Consumer> subs = consumersOf(queueName);
        if (subs.isEmpty()) {
            return;
        }
        int sent = 0;
        while (!q.messages.isEmpty()) {
            Consumer target = nextFree(queueName, subs);
            if (target == null) {
                break;
            }
            Msg m = q.messages.remove(0);
            target.unacked.add(m);
            delivered++;
            flight = new Flight(m.id, m.payload, m.redelivered ? "redelivered" : "delivered");
            flight.queue = queueName;
            flight.consumer = target.name;
            String again = m.redelivered ? " again (redelivered = true)" : "";
            String againRu = m.redelivered ? " повторно (redelivered = true)" : "";
            Trace.event("MESSAGE_DELIVERED",
                    "Broker pushes '" + m.id + "' from '" + queueName + "' to '" + target.name + "'"
                            + again + " — it is now unacked, not deleted",
                    "Брокер проталкивает '" + m.id + "' из '" + queueName + "' потребителю '"
                            + target.name + "'" + againRu + " — сообщение теперь unacked, а не удалено",
                    List.of("queue:" + queueName, "consumer:" + target.name, "msg:" + m.id, "flight"),
                    state());
            sent++;
        }
        if (sent == 0 && !q.messages.isEmpty()) {
            Msg head = q.messages.get(0);
            flight = new Flight(head.id, head.payload, "waiting");
            flight.queue = queueName;
            Trace.event("PREFETCH_BLOCKED",
                    "Every consumer of '" + queueName + "' is at its prefetch limit, so '" + head.id
                            + "' waits in the queue — this is the broker's flow control, not a failure",
                    "Все потребители '" + queueName + "' исчерпали свой prefetch, поэтому '" + head.id
                            + "' ждёт в очереди — это управление потоком у брокера, а не сбой",
                    List.of("queue:" + queueName, "msg:" + head.id, "flight"), state());
        }
    }

    private Consumer nextFree(String queueName, List<Consumer> subs) {
        int start = roundRobin.getOrDefault(queueName, 0);
        for (int i = 0; i < subs.size(); i++) {
            int idx = (start + i) % subs.size();
            Consumer c = subs.get(idx);
            if (c.unacked.size() < c.prefetch) {
                roundRobin.put(queueName, (idx + 1) % subs.size());
                return c;
            }
        }
        return null;
    }

    // --- lookups -----------------------------------------------------------

    private Exchange requireExchange(String name) {
        Exchange ex = exchanges.get(name);
        if (ex == null) {
            throw new IllegalArgumentException("no exchange named '" + name + "' — declare it first");
        }
        return ex;
    }

    private Queue requireQueue(String name) {
        Queue q = queues.get(name);
        if (q == null) {
            throw new IllegalArgumentException("no queue named '" + name + "' — declare it first");
        }
        return q;
    }

    private Consumer requireConsumer(String name) {
        for (Consumer c : consumers) {
            if (c.name.equals(name)) {
                return c;
            }
        }
        throw new IllegalArgumentException("no consumer named '" + name + "' is subscribed");
    }

    private List<Consumer> consumersOf(String queueName) {
        List<Consumer> out = new ArrayList<>();
        for (Consumer c : consumers) {
            if (c.queue.equals(queueName)) {
                out.add(c);
            }
        }
        return out;
    }

    /** Finds the consumer currently holding this message unacknowledged. */
    private Consumer ownerOf(String messageId) {
        for (Consumer c : consumers) {
            for (Msg m : c.unacked) {
                if (m.id.equals(messageId)) {
                    return c;
                }
            }
        }
        throw new IllegalStateException("no unacknowledged message with id '" + messageId
                + "' — it was never delivered, or it was already acked");
    }

    private Msg takeUnacked(Consumer c, String messageId) {
        for (int i = 0; i < c.unacked.size(); i++) {
            if (c.unacked.get(i).id.equals(messageId)) {
                return c.unacked.remove(i);
            }
        }
        throw new IllegalStateException("'" + c.name + "' does not hold '" + messageId + "'");
    }

    // --- state snapshot ----------------------------------------------------

    private Object state() {
        Map<String, Object> s = new LinkedHashMap<>();

        List<Object> exs = new ArrayList<>();
        for (Exchange e : exchanges.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", e.name);
            m.put("type", e.type);
            List<Object> bindings = new ArrayList<>();
            for (Binding b : e.bindings) {
                Map<String, Object> bm = new LinkedHashMap<>();
                bm.put("queue", b.queue);
                bm.put("key", b.key);
                bindings.add(bm);
            }
            m.put("bindings", bindings);
            exs.add(m);
        }
        s.put("exchanges", exs);

        List<Object> qs = new ArrayList<>();
        for (Queue q : queues.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", q.name);
            m.put("deadLetterQueue", q.deadLetterQueue);
            m.put("messages", messages(q.messages));
            qs.add(m);
        }
        s.put("queues", qs);

        List<Object> cs = new ArrayList<>();
        for (Consumer c : consumers) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", c.name);
            m.put("queue", c.queue);
            m.put("prefetch", c.prefetch);
            m.put("unacked", messages(c.unacked));
            cs.add(m);
        }
        s.put("consumers", cs);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("published", published);
        stats.put("delivered", delivered);
        stats.put("acked", acked);
        stats.put("requeued", requeued);
        stats.put("deadLettered", deadLettered);
        stats.put("dropped", dropped);
        s.put("stats", stats);

        if (flight != null) {
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("id", flight.id);
            f.put("payload", flight.payload);
            f.put("stage", flight.stage);
            f.put("exchange", flight.exchange);
            f.put("routingKey", flight.routingKey);
            f.put("queue", flight.queue);
            f.put("consumer", flight.consumer);
            s.put("flight", f);
        }
        return s;
    }

    private static List<Object> messages(List<Msg> src) {
        List<Object> out = new ArrayList<>();
        for (Msg m : src) {
            Map<String, Object> mm = new LinkedHashMap<>();
            mm.put("id", m.id);
            mm.put("payload", m.payload);
            mm.put("redelivered", m.redelivered);
            out.add(mm);
        }
        return out;
    }

    // --- inner types -------------------------------------------------------

    private static final class Exchange {
        final String name;
        /** direct | fanout | topic */
        final String type;
        final List<Binding> bindings = new ArrayList<>();

        Exchange(String name, String type) {
            this.name = name;
            this.type = type;
        }

        boolean matches(String bindingKey, String routingKey) {
            return switch (type) {
                case "fanout" -> true;
                case "topic" -> topicMatches(bindingKey, routingKey);
                default -> bindingKey.equals(routingKey);
            };
        }
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

    private static final class Binding {
        final String queue;
        final String key;

        Binding(String queue, String key) {
            this.queue = queue;
            this.key = key;
        }
    }

    private static final class Queue {
        final String name;
        final String deadLetterQueue;
        /** Ready messages, head first. */
        final List<Msg> messages = new ArrayList<>();

        Queue(String name, String deadLetterQueue) {
            this.name = name;
            this.deadLetterQueue = deadLetterQueue;
        }
    }

    private static final class Consumer {
        final String name;
        final String queue;
        final int prefetch;
        /** Delivered but not yet acknowledged. */
        final List<Msg> unacked = new ArrayList<>();

        Consumer(String name, String queue, int prefetch) {
            this.name = name;
            this.queue = queue;
            this.prefetch = prefetch;
        }
    }

    private static final class Msg {
        final String id;
        final String payload;
        boolean redelivered;

        Msg(String id, String payload) {
            this.id = id;
            this.payload = payload;
        }
    }

    private static final class Flight {
        final String id;
        final String payload;
        /**
         * published | routed | unroutable | delivered | redelivered | waiting |
         * acked | requeued | dead-lettered | dropped
         */
        String stage;
        String exchange = "";
        String routingKey = "";
        String queue = "";
        String consumer = "";

        Flight(String id, String payload, String stage) {
            this.id = id;
            this.payload = payload;
            this.stage = stage;
        }
    }
}
