package visual;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualJpaEntityLifecycleTest {

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
    void transientEntityCanBecomeManagedAndFlushInsert() {
        String out = captureTrace(() -> {
            VisualJpaEntityLifecycle jpa = new VisualJpaEntityLifecycle("orders");
            VisualJpaEntityLifecycle.Entity order = jpa.newEntity("order", "Order draft");
            jpa.persist(order);
            jpa.flush();
        });

        assertEvent(out, "JPA_ENTITY_TRANSIENT");
        assertEvent(out, "JPA_PERSIST");
        assertEvent(out, "JPA_FLUSH");
        assertTrue(out.contains("INSERT INTO entities"),
                "expected INSERT SQL in trace, got:\n" + out);
    }

    @Test
    void detachedEntityIsMergedIntoManagedCopy() {
        String out = captureTrace(() -> {
            VisualJpaEntityLifecycle jpa = new VisualJpaEntityLifecycle("orders");
            jpa.seedRow(10, "Order #10");
            VisualJpaEntityLifecycle.Entity detached = jpa.find("order", 10);
            jpa.detach(detached);
            jpa.change(detached, "Order #10 edited");
            jpa.merge(detached, "managedCopy");
            jpa.flush();
        });

        assertEvent(out, "JPA_FIND");
        assertEvent(out, "JPA_DETACH");
        assertEvent(out, "JPA_CHANGE");
        assertEvent(out, "JPA_MERGE");
        assertTrue(out.contains("\"state\":\"DETACHED\""),
                "expected original object to remain detached, got:\n" + out);
        assertTrue(out.contains("UPDATE entities"),
                "expected UPDATE SQL after merge, got:\n" + out);
    }

    @Test
    void removeManagedEntityEmitsDeleteOnFlush() {
        String out = captureTrace(() -> {
            VisualJpaEntityLifecycle jpa = new VisualJpaEntityLifecycle("orders");
            jpa.seedRow(20, "Order #20");
            VisualJpaEntityLifecycle.Entity order = jpa.find("order", 20);
            jpa.remove(order);
            jpa.flush();
        });

        assertEvent(out, "JPA_REMOVE");
        assertTrue(out.contains("DELETE FROM entities"),
                "expected DELETE SQL in trace, got:\n" + out);
        assertTrue(out.contains("\"deleted\":true"),
                "expected database row to be marked deleted, got:\n" + out);
    }

    @Test
    void closeDetachesManagedEntities() {
        String out = captureTrace(() -> {
            VisualJpaEntityLifecycle jpa = new VisualJpaEntityLifecycle("orders");
            jpa.seedRow(30, "Order #30");
            jpa.find("order", 30);
            jpa.close();
        });

        assertEvent(out, "JPA_CLOSE");
        assertTrue(out.contains("\"contextOpen\":false"),
                "expected closed context in state, got:\n" + out);
        assertTrue(out.contains("\"state\":\"DETACHED\""),
                "expected managed object to become detached, got:\n" + out);
    }

    @Test
    void everyTraceLineIsPrefixed() {
        String out = captureTrace(() -> {
            VisualJpaEntityLifecycle jpa = new VisualJpaEntityLifecycle("orders");
            jpa.seedRow(40, "Order #40");
            VisualJpaEntityLifecycle.Entity order = jpa.find("order", 40);
            jpa.detach(order);
        });

        out.lines().forEach(line -> {
            if (!line.isEmpty()) {
                assertTrue(line.startsWith(Trace.PREFIX),
                        "unexpected non-trace line: " + line);
            }
        });
    }

    private static void assertEvent(String out, String event) {
        assertTrue(out.contains("\"event\":\"" + event + "\""),
                "expected " + event + ", got:\n" + out);
    }
}
