package visual;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A <em>teaching model</em> of the <strong>Inbox pattern</strong> (also called
 * the transactional inbox / idempotent consumer). It is not a library — it
 * reproduces exactly the idea an interviewer asks about: a message broker
 * guarantees <em>at-least-once</em> delivery, so the same message can arrive
 * more than once. A naive consumer would apply the side effect twice (double
 * charge, duplicate order). The Inbox pattern makes consumption idempotent.
 *
 * <p>The model keeps an <em>inbox table</em> of message ids that have already
 * been processed. For every incoming message it runs, in a single local
 * database transaction:
 * <ol>
 *   <li>a dedup check against the inbox table (by the message's unique id), and</li>
 *   <li>only if the id is new: record the id <em>and</em> apply the business
 *       effect together, so the two either both commit or both roll back.</li>
 * </ol>
 * Because the dedup record and the business work share one ACID transaction,
 * at-least-once delivery becomes <em>effectively-once</em> processing.
 *
 * <p>The running {@code effect} total (e.g. the amount charged) makes the point
 * visible: a redelivered message is skipped and never moves the total.
 *
 * <p>Every step emits a {@link Trace} event with a bilingual description so the
 * UI can replay the stream without re-running the code. The model is
 * intentionally dependency-free.
 */
public class VisualInbox {

    private final String name;

    /** Message ids already processed, in arrival order. This is the inbox table. */
    private final Map<String, Entry> inbox = new LinkedHashMap<>();

    private int processedCount;
    private int skippedCount;
    /** Cumulative business effect, e.g. the total amount charged. */
    private int effect;

    /** The message currently being handled (null between receives). */
    private Message current;

    public VisualInbox() {
        this("inbox");
    }

    public VisualInbox(String name) {
        this.name = name;
        Trace.event("INBOX_CREATED",
                "Created an empty inbox '" + name + "' — no message has been processed yet",
                "Создан пустой inbox '" + name + "' — ещё ни одно сообщение не обработано",
                List.of(), state());
    }

    /** Convenience overload for messages whose business effect is a single unit. */
    public boolean receive(String messageId, String payload) {
        return receive(messageId, payload, 1);
    }

    /**
     * Consumes one message delivered by the broker. Inside a single local
     * transaction it deduplicates by {@code messageId} and, only for a new id,
     * records it and applies {@code amount} to the running effect.
     *
     * @return {@code true} if the message was processed now, {@code false} if it
     *         was a duplicate that had already been processed before
     */
    public boolean receive(String messageId, String payload, int amount) {
        current = new Message(messageId, payload, amount, "received");
        Trace.event("MESSAGE_RECEIVED",
                "Message '" + messageId + "' arrives from the broker (" + payload
                        + ", amount " + amount + ")",
                "Сообщение '" + messageId + "' приходит от брокера (" + payload
                        + ", сумма " + amount + ")",
                List.of("message"), state());

        // One local transaction: the dedup check and the business write below
        // commit together, which is what makes the consumer idempotent.
        if (inbox.containsKey(messageId)) {
            current.outcome = "duplicate";
            skippedCount++;
            Trace.event("DUPLICATE_SKIPPED",
                    "'" + messageId + "' is already in the inbox — this is an at-least-once "
                            + "redelivery, so skip it and apply no effect",
                    "'" + messageId + "' уже есть в inbox — это повторная доставка (at-least-once), "
                            + "поэтому пропускаем и эффект не применяем",
                    List.of("message", "inbox:" + messageId), state());
            return false;
        }

        inbox.put(messageId, new Entry(messageId, payload, amount));
        effect += amount;
        processedCount++;
        current.outcome = "processed";
        Trace.event("MESSAGE_PROCESSED",
                "New id '" + messageId + "': record it in the inbox AND apply the effect (+"
                        + amount + ") in the same transaction — total is now " + effect,
                "Новый id '" + messageId + "': записываем в inbox И применяем эффект (+"
                        + amount + ") в одной транзакции — итог теперь " + effect,
                List.of("message", "inbox:" + messageId), state());
        return true;
    }

    /**
     * Purges all but the most recent {@code keepLast} inbox ids, modelling a
     * retention policy that keeps the table from growing forever. This is also a
     * classic trap: if the retention window is shorter than the broker's maximum
     * redelivery delay, a late duplicate of a purged id will be processed again.
     */
    public void cleanup(int keepLast) {
        current = null;
        int toRemove = inbox.size() - Math.max(0, keepLast);
        if (toRemove > 0) {
            List<String> ids = new ArrayList<>(inbox.keySet());
            for (int i = 0; i < toRemove; i++) {
                inbox.remove(ids.get(i));
            }
        }
        Trace.event("INBOX_CLEANUP",
                "Cleanup: keep only the last " + keepLast + " inbox ids, older ones are purged "
                        + "(the retention window must outlast the broker's max redelivery delay)",
                "Очистка: оставляем только последние " + keepLast + " id в inbox, старые удаляем "
                        + "(окно ретенции должно превышать максимальную задержку повторной доставки)",
                List.of(), state());
    }

    /** Builds the JSON-serializable snapshot consumed by the visualizer. */
    private Object state() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("name", name);

        List<Object> entries = new ArrayList<>();
        for (Entry e : inbox.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", e.id);
            m.put("payload", e.payload);
            m.put("amount", e.amount);
            entries.add(m);
        }
        s.put("inbox", entries);

        s.put("processedCount", processedCount);
        s.put("skippedCount", skippedCount);
        s.put("effect", effect);

        if (current != null) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", current.id);
            m.put("payload", current.payload);
            m.put("amount", current.amount);
            m.put("outcome", current.outcome);
            s.put("message", m);
        }
        return s;
    }

    private static final class Entry {
        final String id;
        final String payload;
        final int amount;

        Entry(String id, String payload, int amount) {
            this.id = id;
            this.payload = payload;
            this.amount = amount;
        }
    }

    private static final class Message {
        final String id;
        final String payload;
        final int amount;
        /** received | processed | duplicate */
        String outcome;

        Message(String id, String payload, int amount, String outcome) {
            this.id = id;
            this.payload = payload;
            this.amount = amount;
            this.outcome = outcome;
        }
    }
}
