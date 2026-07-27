package visual;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualBrokerTest {

    private String captureTrace(Runnable body) {
        PrintStream original = System.out;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buffer, true, StandardCharsets.UTF_8));
        try {
            body.run();
        } finally {
            System.setOut(original);
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }

    @Test
    void creatingABrokerEmitsCreated() {
        String out = captureTrace(VisualBroker::new);
        assertTrue(out.contains("BROKER_CREATED"), "expected a creation event, got:\n" + out);
    }

    @Test
    void publishRoutesThroughTheExchangeAndIsPushedToTheConsumer() {
        String out = captureTrace(() -> {
            VisualBroker broker = new VisualBroker();
            broker.declareExchange("orders", "direct");
            broker.declareQueue("orders.created");
            broker.bind("orders", "orders.created", "created");
            broker.subscribe("worker-1", "orders.created", 1);
            broker.publish("orders", "created", "m1", "order #1");
            broker.ack("m1");
        });
        assertTrue(out.contains("EXCHANGE_DECLARED"), "expected an exchange event, got:\n" + out);
        assertTrue(out.contains("QUEUE_BOUND"), "expected a binding event, got:\n" + out);
        assertTrue(out.contains("MESSAGE_PUBLISHED"), "expected a publish event, got:\n" + out);
        assertTrue(out.contains("MESSAGE_ROUTED"), "expected a routing event, got:\n" + out);
        assertTrue(out.contains("MESSAGE_DELIVERED"), "expected a delivery event, got:\n" + out);
        assertTrue(out.contains("MESSAGE_ACKED"), "expected an ack event, got:\n" + out);
    }

    @Test
    void aMessageMatchingNoBindingIsUnroutable() {
        String out = captureTrace(() -> {
            VisualBroker broker = new VisualBroker();
            broker.declareExchange("orders", "direct");
            broker.declareQueue("orders.created");
            broker.bind("orders", "orders.created", "created");
            broker.publish("orders", "shipped", "m1", "order #1");
        });
        assertTrue(out.contains("MESSAGE_UNROUTABLE"), "expected an unroutable event, got:\n" + out);
        assertFalse(out.contains("MESSAGE_ROUTED"), "nothing should be enqueued, got:\n" + out);
    }

    @Test
    void fanoutGivesEveryBoundQueueItsOwnCopy() {
        String out = captureTrace(() -> {
            VisualBroker broker = new VisualBroker();
            broker.declareExchange("events", "fanout");
            broker.declareQueue("audit");
            broker.declareQueue("search-index");
            broker.bind("events", "audit");
            broker.bind("events", "search-index");
            broker.publish("events", "ignored", "e1", "user updated");
        });
        assertTrue(out.contains("queue 'audit'"), "audit must get a copy, got:\n" + out);
        assertTrue(out.contains("queue 'search-index'"), "search-index must get a copy, got:\n" + out);
    }

    @Test
    void topicWildcardsMatchWordsAndTails() {
        String out = captureTrace(() -> {
            VisualBroker broker = new VisualBroker();
            broker.declareExchange("logs", "topic");
            broker.declareQueue("logs.error");
            broker.declareQueue("logs.payment");
            broker.bind("logs", "logs.error", "*.error");
            broker.bind("logs", "logs.payment", "payment.#");
            // Matches both patterns.
            broker.publish("logs", "payment.error", "l1", "card declined");
            // '*' is exactly one word, so 'payment.#' matches but '*.error' does not.
            broker.publish("logs", "payment.retry.ok", "l2", "retry ok");
        });
        assertTrue(out.contains("'l1' is stored at the tail of queue 'logs.error'"),
                "'*.error' must match payment.error, got:\n" + out);
        assertTrue(out.contains("'l2' is stored at the tail of queue 'logs.payment'"),
                "'payment.#' must match a longer key, got:\n" + out);
        assertFalse(out.contains("'l2' is stored at the tail of queue 'logs.error'"),
                "'*' must match exactly one word, got:\n" + out);
    }

    @Test
    void competingConsumersShareTheQueueAndPrefetchHoldsMessagesBack() {
        String out = captureTrace(() -> {
            VisualBroker broker = new VisualBroker();
            broker.declareExchange("tasks", "direct");
            broker.declareQueue("tasks.run");
            broker.bind("tasks", "tasks.run", "run");
            broker.subscribe("worker-1", "tasks.run", 1);
            broker.subscribe("worker-2", "tasks.run", 1);
            broker.publish("tasks", "run", "t1", "task 1");
            broker.publish("tasks", "run", "t2", "task 2");
            broker.publish("tasks", "run", "t3", "task 3");
        });
        assertTrue(out.contains("'t1' from 'tasks.run' to 'worker-1'"),
                "the first task goes to worker-1, got:\n" + out);
        assertTrue(out.contains("'t2' from 'tasks.run' to 'worker-2'"),
                "round-robin must move to worker-2, got:\n" + out);
        assertTrue(out.contains("PREFETCH_BLOCKED"),
                "with both consumers at prefetch 1 the third task must wait, got:\n" + out);
    }

    @Test
    void ackingFreesAPrefetchSlotSoTheNextMessageIsPushed() {
        String out = captureTrace(() -> {
            VisualBroker broker = new VisualBroker();
            broker.declareExchange("tasks", "direct");
            broker.declareQueue("tasks.run");
            broker.bind("tasks", "tasks.run", "run");
            broker.subscribe("worker-1", "tasks.run", 1);
            broker.publish("tasks", "run", "t1", "task 1");
            broker.publish("tasks", "run", "t2", "task 2");
            broker.ack("t1");
        });
        assertTrue(out.contains("'t2' from 'tasks.run' to 'worker-1'"),
                "acking t1 must release t2, got:\n" + out);
    }

    @Test
    void aConsumerThatDiesBeforeAckingCausesARedelivery() {
        String out = captureTrace(() -> {
            VisualBroker broker = new VisualBroker();
            broker.declareExchange("orders", "direct");
            broker.declareQueue("orders.created");
            broker.bind("orders", "orders.created", "created");
            broker.subscribe("worker-1", "orders.created", 1);
            broker.publish("orders", "created", "m1", "order #7");
            broker.crashConsumer("worker-1");
            broker.subscribe("worker-2", "orders.created", 1);
        });
        assertTrue(out.contains("CONSUMER_CRASHED"), "expected a crash event, got:\n" + out);
        assertTrue(out.contains("'m1' from 'orders.created' to 'worker-2' again (redelivered = true)"),
                "the unacked message must be redelivered, got:\n" + out);
    }

    @Test
    void rejectWithoutRequeueDeadLettersOrLosesTheMessage() {
        String withDlq = captureTrace(() -> {
            VisualBroker broker = new VisualBroker();
            broker.declareExchange("payments", "direct");
            broker.declareQueue("payments.dlq");
            broker.declareQueue("payments.charge", "payments.dlq");
            broker.bind("payments", "payments.charge", "charge");
            broker.subscribe("worker-1", "payments.charge", 1);
            broker.publish("payments", "charge", "p1", "charge 100");
            broker.nack("p1", true);    // back to the head, redelivered
            broker.nack("p1", false);   // give up -> dead-letter queue
        });
        assertTrue(withDlq.contains("MESSAGE_REQUEUED"), "expected a requeue event, got:\n" + withDlq);
        assertTrue(withDlq.contains("MESSAGE_DEAD_LETTERED"),
                "expected a dead-letter event, got:\n" + withDlq);

        String withoutDlq = captureTrace(() -> {
            VisualBroker broker = new VisualBroker();
            broker.declareExchange("mail", "direct");
            broker.declareQueue("mail.send");
            broker.bind("mail", "mail.send", "send");
            broker.subscribe("worker-1", "mail.send", 1);
            broker.publish("mail", "send", "e1", "send receipt");
            broker.nack("e1", false);
        });
        assertTrue(withoutDlq.contains("MESSAGE_DROPPED"),
                "no dead-letter target means the message is lost, got:\n" + withoutDlq);
    }

    @Test
    void aQueueBuffersWhileNoConsumerIsAttached() {
        String out = captureTrace(() -> {
            VisualBroker broker = new VisualBroker();
            broker.declareExchange("jobs", "direct");
            broker.declareQueue("jobs.resize");
            broker.bind("jobs", "jobs.resize", "resize");
            broker.publish("jobs", "resize", "j1", "photo-1");
            broker.publish("jobs", "resize", "j2", "photo-2");
            broker.subscribe("worker-1", "jobs.resize", 2);
        });
        assertTrue(out.contains("(depth 2)"), "the queue must buffer both jobs, got:\n" + out);
        assertTrue(out.contains("'j1' from 'jobs.resize' to 'worker-1'"),
                "a late consumer drains the backlog, got:\n" + out);
        assertTrue(out.contains("'j2' from 'jobs.resize' to 'worker-1'"),
                "prefetch 2 lets both be pushed, got:\n" + out);
    }

    @Test
    void ackingAnUnknownMessageFailsLoudly() {
        assertThrows(IllegalStateException.class, () -> {
            VisualBroker broker = new VisualBroker();
            broker.ack("nope");
        });
    }

    @Test
    void everyTraceLineIsPrefixed() {
        String out = captureTrace(() -> {
            VisualBroker broker = new VisualBroker();
            broker.declareExchange("orders", "direct");
            broker.declareQueue("orders.created");
            broker.bind("orders", "orders.created", "created");
            broker.subscribe("worker-1", "orders.created", 1);
            broker.publish("orders", "created", "m1", "order #1");
            broker.ack("m1");
        });
        out.lines().forEach(line -> {
            if (!line.isEmpty()) {
                assertTrue(line.startsWith(Trace.PREFIX), "unexpected non-trace line: " + line);
            }
        });
    }
}
