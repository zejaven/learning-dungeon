package visual;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualRouterTest {

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
    void declaringAnExchangeEmitsAnEvent() {
        String out = captureTrace(() -> new VisualRouter("orders", "direct"));
        assertTrue(out.contains("EXCHANGE_DECLARED"), "expected a declaration event, got:\n" + out);
    }

    @Test
    void anUnknownExchangeTypeFailsLoudly() {
        assertThrows(IllegalArgumentException.class, () -> new VisualRouter("orders", "headers"));
    }

    @Test
    void aDirectExchangeMatchesTheKeysExactly() {
        String out = captureTrace(() -> {
            VisualRouter router = new VisualRouter("orders", "direct");
            router.bind("invoices", "order.paid");
            router.publish("order.paid", "m1");
        });
        assertTrue(out.contains("BINDING_ADDED"), "expected a binding event, got:\n" + out);
        assertTrue(out.contains("MESSAGE_PUBLISHED"), "expected a publish event, got:\n" + out);
        assertTrue(out.contains("BINDING_MATCHED"), "the keys are equal, got:\n" + out);
        assertTrue(out.contains("MESSAGE_ROUTED"), "expected a routing event, got:\n" + out);
    }

    @Test
    void aDirectExchangeRejectsADifferentKey() {
        String out = captureTrace(() -> {
            VisualRouter router = new VisualRouter("orders", "direct");
            router.bind("invoices", "order.paid");
            router.publish("order.cancelled", "m1");
        });
        assertTrue(out.contains("BINDING_SKIPPED"), "the keys differ, got:\n" + out);
        assertTrue(out.contains("MESSAGE_UNROUTABLE"), "nothing matched, got:\n" + out);
        assertFalse(out.contains("MESSAGE_ROUTED"), "nothing may be stored, got:\n" + out);
    }

    @Test
    void oneBindingKeyCanFeedSeveralQueuesAndOneQueueCanHaveSeveralKeys() {
        String out = captureTrace(() -> {
            VisualRouter router = new VisualRouter("orders", "direct");
            router.bind("billing", "order.paid");
            router.bind("analytics", "order.paid");
            router.bind("billing", "order.refunded");
            router.publish("order.paid", "m1");
            router.publish("order.refunded", "m2");
        });
        assertTrue(out.contains("stored in 2 queue(s): billing, analytics"),
                "one binding key must feed both queues, got:\n" + out);
        assertTrue(out.contains("stored in 1 queue(s): billing"),
                "the second binding key must feed billing alone, got:\n" + out);
    }

    @Test
    void wildcardsOnlyMeanSomethingInABindingKey() {
        String out = captureTrace(() -> {
            VisualRouter router = new VisualRouter("orders", "topic");
            router.bind("paid", "order.paid");
            router.bind("shipped", "order.shipped");
            // A '*' inside a routing key is a literal character, not a wildcard.
            router.publish("order.*", "m1");
        });
        assertTrue(out.contains("WILDCARD_IN_ROUTING_KEY"),
                "a wildcard character in the routing key must be called out, got:\n" + out);
        assertTrue(out.contains("MESSAGE_UNROUTABLE"),
                "'order.*' as a routing key matches neither binding, got:\n" + out);
    }

    @Test
    void topicPatternsMatchWordsAndTails() {
        String out = captureTrace(() -> {
            VisualRouter router = new VisualRouter("logs", "topic");
            router.bind("errors", "*.error");
            router.bind("payments", "payment.#");
            router.bind("archive", "#");
            router.publish("payment.error", "l1");
            router.publish("payment.retry.ok", "l2");
        });
        assertTrue(out.contains("'l1' is stored in 3 queue(s): errors, payments, archive"),
                "all three patterns must match payment.error, got:\n" + out);
        assertTrue(out.contains("'l2' is stored in 2 queue(s): payments, archive"),
                "'*' spans exactly one word, so '*.error' must miss, got:\n" + out);
    }

    @Test
    void twoMatchingBindingsToOneQueueStillProduceOneCopy() {
        String out = captureTrace(() -> {
            VisualRouter router = new VisualRouter("logs", "topic");
            router.bind("audit", "order.#");
            router.bind("audit", "#.created");
            router.publish("order.created", "m1");
        });
        assertTrue(out.contains("BINDING_DUPLICATE"),
                "the second matching binding must be reported, got:\n" + out);
        assertTrue(out.contains("stored in 1 queue(s): audit"),
                "the queue must receive exactly one copy, got:\n" + out);
    }

    @Test
    void fanoutIgnoresBothKeys() {
        String out = captureTrace(() -> {
            VisualRouter router = new VisualRouter("events", "fanout");
            router.bind("audit");
            router.bind("search-index", "never.read");
            router.publish("user.updated", "e1");
        });
        assertTrue(out.contains("KEYS_IGNORED"), "fanout must announce it reads no key, got:\n" + out);
        assertTrue(out.contains("stored in 2 queue(s): audit, search-index"),
                "every bound queue gets a copy, got:\n" + out);
    }

    @Test
    void theDefaultExchangeBindsEveryQueueByItsOwnName() {
        String out = captureTrace(() -> {
            VisualRouter router = VisualRouter.defaultExchange();
            router.declareQueue("task-queue");
            router.publish("task-queue", "t1");
            router.publish("Task-Queue", "t2");
        });
        assertTrue(out.contains("QUEUE_DECLARED"), "expected a queue event, got:\n" + out);
        assertTrue(out.contains("BINDING_ADDED"), "the broker must bind the queue itself, got:\n" + out);
        assertTrue(out.contains("stored in 1 queue(s): task-queue"),
                "routing key = queue name must reach it, got:\n" + out);
        assertTrue(out.contains("MESSAGE_UNROUTABLE"),
                "matching is case-sensitive, so 'Task-Queue' must go nowhere, got:\n" + out);
    }

    @Test
    void theDefaultExchangeCannotBeBoundByHand() {
        assertThrows(IllegalStateException.class, () -> {
            VisualRouter router = VisualRouter.defaultExchange();
            router.bind("task-queue", "anything");
        });
    }

    @Test
    void aQueueWithoutABindingReceivesNothing() {
        String out = captureTrace(() -> {
            VisualRouter router = new VisualRouter("orders", "direct");
            router.declareQueue("shipping");
            router.publish("shipping", "m1");
        });
        assertTrue(out.contains("MESSAGE_UNROUTABLE"),
                "an unbound queue is unreachable even by its own name, got:\n" + out);
    }

    @Test
    void everyTraceLineIsPrefixed() {
        String out = captureTrace(() -> {
            VisualRouter router = new VisualRouter("orders", "topic");
            router.bind("audit", "order.#");
            router.publish("order.paid", "m1");
        });
        out.lines().forEach(line -> {
            if (!line.isEmpty()) {
                assertTrue(line.startsWith(Trace.PREFIX), "unexpected non-trace line: " + line);
            }
        });
    }
}
