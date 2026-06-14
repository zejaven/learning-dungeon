package visual;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A <em>teaching model</em> of the <strong>Outbox pattern</strong> (also called
 * the transactional outbox). It is not a library — it reproduces exactly the
 * problem an interviewer asks about: a service must both <em>change its
 * database</em> and <em>publish an event</em> to a message broker, but those are
 * two different systems. Doing them as two separate steps is the
 * <em>dual-write problem</em>: a crash in between leaves the database and the
 * broker inconsistent (the order was saved but the event was never sent, or the
 * event was sent but the database rolled back).
 *
 * <p>The Outbox pattern fixes this. In <strong>one local database
 * transaction</strong> the service writes the business change <em>and</em>
 * inserts an event row into an <em>outbox table</em> living in the same
 * database. Because both writes share one ACID transaction, they commit or roll
 * back together — there is no window where one happened without the other.
 *
 * <p>A separate <em>relay</em> (a poller, or change-data-capture) later reads
 * the unpublished outbox rows and publishes them to the broker, marking each as
 * published. The relay is <em>at-least-once</em>: if it crashes after sending
 * but before marking a row published, it will resend that event — so consumers
 * must dedup (see the Inbox pattern). This model makes all of that visible:
 *
 * <ul>
 *   <li>the <b>business table</b> (orders),</li>
 *   <li>the <b>outbox table</b> (event rows, each PENDING or PUBLISHED),</li>
 *   <li>the <b>broker</b> (events actually delivered).</li>
 * </ul>
 *
 * <p>Every step emits a {@link Trace} event with a bilingual description so the
 * UI can replay the stream without re-running the code. The model is
 * intentionally dependency-free.
 */
public class VisualOutbox {

    private final String name;

    /** Business table: order id -> amount. */
    private final Map<String, Integer> orders = new LinkedHashMap<>();
    /** Outbox table: event rows staged in the same DB as the business data. */
    private final List<Row> outbox = new ArrayList<>();
    /** Events the broker has actually received (what consumers would see). */
    private final List<Delivered> broker = new ArrayList<>();

    private int seq;            // event id sequence
    private int committed;      // transactions committed (business + outbox)
    private int published;      // events successfully published by the relay
    private int redelivered;    // events the relay sent more than once (duplicates)
    private int lost;           // events lost by a dual write (no outbox)

    /** The action currently shown in the banner (null between actions). */
    private Action current;

    public VisualOutbox() {
        this("outbox");
    }

    public VisualOutbox(String name) {
        this.name = name;
        Trace.event("OUTBOX_CREATED",
                "Created an empty outbox '" + name + "' — business table and outbox table both empty",
                "Создан пустой outbox '" + name + "' — таблица бизнес-данных и таблица outbox пусты",
                List.of(), state());
    }

    /**
     * The happy path. In <em>one</em> local transaction it writes the order to
     * the business table AND stages an event row in the outbox table. Both
     * commit together, so the outbox row can never get out of sync with the
     * business change.
     */
    public void placeOrder(String orderId, int amount) {
        String eventId = "e" + (++seq);
        current = new Action(orderId, "amount " + amount, "committed");
        orders.put(orderId, amount);
        outbox.add(new Row(eventId, "OrderPlaced", orderId + ":" + amount, "PENDING"));
        committed++;
        Trace.event("TRANSACTION_COMMITTED",
                "One local transaction: save order '" + orderId + "' AND stage event " + eventId
                        + " in the outbox — both commit together",
                "Одна локальная транзакция: сохраняем заказ '" + orderId + "' И кладём событие "
                        + eventId + " в outbox — фиксируются вместе",
                List.of("action", "order:" + orderId, "outbox:" + eventId), state());
    }

    /**
     * A transaction that fails business validation and rolls back. Because the
     * business write and the outbox insert share one transaction, the rollback
     * undoes <em>both</em>: no order is saved and no event is staged. There is
     * never a staged event for a change that did not happen.
     */
    public void placeOrderFailing(String orderId, int amount) {
        current = new Action(orderId, "amount " + amount, "rolledback");
        // The order and the outbox row would have been written here, but the
        // transaction rolls back, so neither is persisted.
        Trace.event("TRANSACTION_ROLLED_BACK",
                "Transaction for order '" + orderId + "' fails and rolls back — neither the order "
                        + "nor any outbox event is persisted (atomic)",
                "Транзакция для заказа '" + orderId + "' падает и откатывается — ни заказ, ни "
                        + "событие в outbox не сохраняются (атомарно)",
                List.of("action"), state());
    }

    /**
     * The relay (poller). Reads every PENDING outbox row, publishes it to the
     * broker, and marks it PUBLISHED. If the broker already received an event
     * with the same id, the send is a <em>redelivery</em> (a duplicate) rather
     * than a fresh publish.
     */
    public void relayPublish() {
        boolean any = false;
        for (Row row : outbox) {
            if (!"PENDING".equals(row.status)) {
                continue;
            }
            any = true;
            sendToBroker(row);
            row.status = "PUBLISHED";
        }
        if (!any) {
            current = null;
            Trace.event("EVENT_PUBLISHED",
                    "Relay polls the outbox — nothing PENDING, so there is nothing to publish",
                    "Релей опрашивает outbox — нет PENDING-записей, публиковать нечего",
                    List.of(), state());
        }
    }

    /**
     * Models a relay crash: it sends the first PENDING event to the broker but
     * crashes <em>before</em> marking the row PUBLISHED. The broker has the
     * event, yet the row stays PENDING — so the next poll will send it again.
     * This is why the relay is at-least-once and consumers must dedup.
     */
    public void relayCrashAfterSend() {
        for (Row row : outbox) {
            if ("PENDING".equals(row.status)) {
                sendToBroker(row);
                // Crash here: the row is NOT marked PUBLISHED.
                return;
            }
        }
    }

    /**
     * The anti-pattern the Outbox exists to prevent: a <em>dual write</em>. The
     * service commits the business change to the database and then, as a
     * separate step, tries to publish to the broker. Here the publish fails
     * after the commit — the order is saved but the event is lost forever, with
     * no outbox row to recover it.
     */
    public void placeOrderDualWrite(String orderId, int amount) {
        current = new Action(orderId, "amount " + amount, "lost");
        orders.put(orderId, amount);   // business commit (step 1)
        // Step 2 is a separate publish that crashes — and with no outbox table,
        // there is no record of the event to retry. It is simply gone.
        lost++;
        Trace.event("DUAL_WRITE_LOST",
                "Dual write: order '" + orderId + "' is committed, then the separate publish crashes "
                        + "— with no outbox the event is lost forever",
                "Двойная запись: заказ '" + orderId + "' зафиксирован, затем отдельная публикация "
                        + "падает — без outbox событие потеряно навсегда",
                List.of("action", "order:" + orderId), state());
    }

    /** Sends one row to the broker, distinguishing a fresh publish from a duplicate. */
    private void sendToBroker(Row row) {
        boolean dup = false;
        for (Delivered d : broker) {
            if (d.eventId.equals(row.eventId)) {
                dup = true;
                break;
            }
        }
        broker.add(new Delivered(row.eventId, row.type, row.payload, dup));
        if (dup) {
            redelivered++;
            current = new Action(row.eventId, row.payload, "redelivered");
            Trace.event("EVENT_REDELIVERED",
                    "Relay sends " + row.eventId + " again — the broker already had it, so this is a "
                            + "duplicate delivery (consumers must dedup, e.g. an Inbox)",
                    "Релей снова отправляет " + row.eventId + " — у брокера он уже был, это "
                            + "дублирующая доставка (потребители должны дедуплицировать, напр. Inbox)",
                    List.of("action", "outbox:" + row.eventId, "broker:" + row.eventId), state());
        } else {
            published++;
            current = new Action(row.eventId, row.payload, "published");
            Trace.event("EVENT_PUBLISHED",
                    "Relay publishes " + row.eventId + " from the outbox to the broker and marks the "
                            + "row PUBLISHED",
                    "Релей публикует " + row.eventId + " из outbox в брокер и помечает запись "
                            + "PUBLISHED",
                    List.of("action", "outbox:" + row.eventId, "broker:" + row.eventId), state());
        }
    }

    /** Builds the JSON-serializable snapshot consumed by the visualizer. */
    private Object state() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("name", name);

        List<Object> orderList = new ArrayList<>();
        for (Map.Entry<String, Integer> e : orders.entrySet()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", e.getKey());
            m.put("amount", e.getValue());
            orderList.add(m);
        }
        s.put("orders", orderList);

        List<Object> outboxList = new ArrayList<>();
        for (Row r : outbox) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("eventId", r.eventId);
            m.put("type", r.type);
            m.put("payload", r.payload);
            m.put("status", r.status);
            outboxList.add(m);
        }
        s.put("outbox", outboxList);

        List<Object> brokerList = new ArrayList<>();
        for (Delivered d : broker) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("eventId", d.eventId);
            m.put("type", d.type);
            m.put("payload", d.payload);
            m.put("duplicate", d.duplicate);
            brokerList.add(m);
        }
        s.put("broker", brokerList);

        s.put("committed", committed);
        s.put("published", published);
        s.put("redelivered", redelivered);
        s.put("lost", lost);

        if (current != null) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", current.id);
            m.put("info", current.info);
            m.put("outcome", current.outcome);
            s.put("action", m);
        }
        return s;
    }

    private static final class Row {
        final String eventId;
        final String type;
        final String payload;
        /** PENDING | PUBLISHED */
        String status;

        Row(String eventId, String type, String payload, String status) {
            this.eventId = eventId;
            this.type = type;
            this.payload = payload;
            this.status = status;
        }
    }

    private static final class Delivered {
        final String eventId;
        final String type;
        final String payload;
        final boolean duplicate;

        Delivered(String eventId, String type, String payload, boolean duplicate) {
            this.eventId = eventId;
            this.type = type;
            this.payload = payload;
            this.duplicate = duplicate;
        }
    }

    private static final class Action {
        final String id;
        final String info;
        /** committed | rolledback | published | redelivered | lost */
        final String outcome;

        Action(String id, String info, String outcome) {
            this.id = id;
            this.info = info;
            this.outcome = outcome;
        }
    }
}
