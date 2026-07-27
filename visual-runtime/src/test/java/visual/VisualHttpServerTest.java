package visual;

import org.junit.jupiter.api.Test;
import visual.VisualHttpServer.Body;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualHttpServerTest {

    private static VisualHttpServer shop() {
        return VisualHttpServer.serving("/orders",
                Body.of("item", "tea").and("qty", "2").and("status", "new"),
                Body.of("item", "cups").and("qty", "6").and("status", "paid"));
    }

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
    void servingASeededCollectionEmitsReady() {
        String out = captureTrace(VisualHttpServerTest::shop);
        assertTrue(out.contains("SERVER_READY"), "expected a startup event, got:\n" + out);
        assertTrue(out.contains("/orders/1, /orders/2"), "the seeded paths must be visible, got:\n" + out);
    }

    @Test
    void getReturnsARepresentationAndChangesNothing() {
        String out = captureTrace(() -> {
            VisualHttpServer api = shop();
            api.get("/orders/1");
            api.report();
        });
        assertTrue(out.contains("RESPONSE_RECEIVED"), "expected a response event, got:\n" + out);
        assertTrue(out.contains("SAFE_READ"), "a GET must be reported as safe, got:\n" + out);
        assertFalse(out.contains("STATE_CHANGED"), "a GET may not change state, got:\n" + out);
        assertTrue(out.contains("0 changed server state"), "no write may be counted, got:\n" + out);
    }

    @Test
    void repeatingAGetIsAnsweredFromTheCache() {
        String out = captureTrace(() -> {
            VisualHttpServer api = shop();
            api.get("/orders/1");
            api.get("/orders/1");
        });
        assertTrue(out.contains("CACHE_HIT"), "the second GET must hit the cache, got:\n" + out);
    }

    @Test
    void aWriteInvalidatesTheCachedCopy() {
        String out = captureTrace(() -> {
            VisualHttpServer api = shop();
            api.get("/orders/1");
            api.patch("/orders/1", Body.of("status", "paid"));
            api.get("/orders/1");
        });
        assertFalse(out.contains("CACHE_HIT"),
                "a write must make the cached copy stale, got:\n" + out);
    }

    @Test
    void headReturnsTheHeadersWithoutABody() {
        String out = captureTrace(() -> {
            VisualHttpServer api = shop();
            api.head("/orders/1");
        });
        assertTrue(out.contains("HEAD_NO_BODY"), "expected the HEAD event, got:\n" + out);
        assertTrue(out.contains("SAFE_READ"), "HEAD is safe too, got:\n" + out);
    }

    @Test
    void optionsListsTheMethodsTheUrlSupports() {
        String out = captureTrace(() -> {
            VisualHttpServer api = shop();
            api.options("/orders");
        });
        assertTrue(out.contains("OPTIONS_ALLOW"), "expected the OPTIONS event, got:\n" + out);
        assertTrue(out.contains("Allow: GET, HEAD, POST, OPTIONS"),
                "the collection must advertise its methods, got:\n" + out);
    }

    @Test
    void postLetsTheServerChooseTheUrl() {
        String out = captureTrace(() -> {
            VisualHttpServer api = shop();
            api.post("/orders", Body.of("item", "mugs").and("qty", "1").and("status", "new"));
            api.report();
        });
        assertTrue(out.contains("RESOURCE_CREATED"), "expected a creation event, got:\n" + out);
        assertTrue(out.contains("Location: /orders/3"), "the new URL must be reported, got:\n" + out);
        assertTrue(out.contains("STATE_CHANGED"), "POST is not safe, got:\n" + out);
        assertTrue(out.contains("holds 3 resource(s)"), "the collection must have grown, got:\n" + out);
    }

    @Test
    void postingTheSameBodyTwiceCreatesTwoResources() {
        String out = captureTrace(() -> {
            VisualHttpServer api = shop();
            Body order = Body.of("item", "mugs").and("qty", "1").and("status", "new");
            api.post("/orders", order);
            api.post("/orders", Body.of("item", "mugs").and("qty", "1").and("status", "new"));
            api.report();
        });
        assertTrue(out.contains("DUPLICATE_CREATED"), "POST is not idempotent, got:\n" + out);
        assertTrue(out.contains("holds 4 resource(s)"), "two orders must exist, got:\n" + out);
        assertFalse(out.contains("IDEMPOTENT_REPEAT"), "a repeated POST is not a no-op, got:\n" + out);
    }

    @Test
    void repeatingTheSamePutChangesNothingTheSecondTime() {
        String out = captureTrace(() -> {
            VisualHttpServer api = shop();
            api.put("/orders/1", Body.of("item", "tea").and("qty", "5").and("status", "paid"));
            api.put("/orders/1", Body.of("item", "tea").and("qty", "5").and("status", "paid"));
            api.report();
        });
        assertTrue(out.contains("RESOURCE_UPDATED"), "the first PUT must apply, got:\n" + out);
        assertTrue(out.contains("IDEMPOTENT_REPEAT"), "the second PUT must be a no-op, got:\n" + out);
        assertTrue(out.contains("holds 2 resource(s)"), "PUT may not create a copy, got:\n" + out);
        assertTrue(out.contains("1 changed server state"), "only one write happened, got:\n" + out);
    }

    @Test
    void putCreatesAtTheUrlTheClientNamed() {
        String out = captureTrace(() -> {
            VisualHttpServer api = shop();
            api.put("/orders/9", Body.of("item", "spoons").and("qty", "4").and("status", "new"));
            api.report();
        });
        assertTrue(out.contains("RESOURCE_CREATED"), "PUT must create the resource, got:\n" + out);
        assertTrue(out.contains("201 Created"), "expected a 201, got:\n" + out);
        assertTrue(out.contains("/orders/9"), "the client-chosen URL must be used, got:\n" + out);
    }

    @Test
    void patchOnlyMergesTheFieldsItNames() {
        String out = captureTrace(() -> {
            VisualHttpServer api = shop();
            api.patch("/orders/1", Body.of("status", "paid"));
            api.report();
        });
        assertTrue(out.contains("RESOURCE_UPDATED"), "expected the merge event, got:\n" + out);
        assertTrue(out.contains("item=tea, qty=2, status=paid"),
                "unmentioned fields must survive, got:\n" + out);
    }

    @Test
    void deletingTwiceEndsInTheSameStateWithADifferentStatus() {
        String out = captureTrace(() -> {
            VisualHttpServer api = shop();
            api.delete("/orders/2");
            api.delete("/orders/2");
            api.report();
        });
        assertTrue(out.contains("RESOURCE_DELETED"), "the first DELETE must apply, got:\n" + out);
        assertTrue(out.contains("404 Not Found"), "the second DELETE finds nothing, got:\n" + out);
        assertTrue(out.contains("IDEMPOTENT_REPEAT"),
                "the end state must be reported as unchanged, got:\n" + out);
        assertTrue(out.contains("holds 1 resource(s)"), "only one resource may remain, got:\n" + out);
    }

    @Test
    void aMethodTheUrlDoesNotSupportIsAnsweredWithAllow() {
        String out = captureTrace(() -> {
            VisualHttpServer api = shop();
            api.delete("/orders");
            api.post("/orders/1", Body.of("item", "tea").and("qty", "1"));
            api.report();
        });
        assertTrue(out.contains("METHOD_NOT_ALLOWED"), "expected a 405, got:\n" + out);
        assertTrue(out.contains("405 Method Not Allowed"), "expected the status line, got:\n" + out);
        assertTrue(out.contains("Allow: GET, HEAD, PUT, PATCH, DELETE, OPTIONS"),
                "an item URL must advertise its methods, got:\n" + out);
        assertTrue(out.contains("holds 2 resource(s)"), "a 405 may not change anything, got:\n" + out);
    }

    @Test
    void aGetWhoseHandlerWritesIsReportedAsUnsafe() {
        String out = captureTrace(() -> {
            VisualHttpServer api = shop();
            api.mutatingGet("/orders/1/cancel", "/orders/1", Body.of("status", "cancelled"));
            api.report();
        });
        assertTrue(out.contains("UNSAFE_GET"), "expected the unsafe-read event, got:\n" + out);
        assertTrue(out.contains("STATE_CHANGED"), "the write must be reported, got:\n" + out);
        assertTrue(out.contains("status=cancelled"), "the change must be visible, got:\n" + out);
        assertTrue(out.contains("1 read(s) were not actually safe"),
                "the unsafe read must be counted, got:\n" + out);
    }

    @Test
    void aMissingTargetIsA404AndNotA405() {
        String out = captureTrace(() -> {
            VisualHttpServer api = shop();
            api.get("/orders/99");
        });
        assertTrue(out.contains("NOT_FOUND"), "expected a 404, got:\n" + out);
        assertFalse(out.contains("METHOD_NOT_ALLOWED"), "GET is allowed here, got:\n" + out);
    }

    @Test
    void everyTraceLineIsPrefixed() {
        String out = captureTrace(() -> {
            VisualHttpServer api = shop();
            api.get("/orders");
            api.head("/orders/1");
            api.options("/orders/1");
            api.post("/orders", Body.of("item", "mugs").and("qty", "1"));
            api.put("/orders/1", Body.of("item", "tea").and("qty", "9"));
            api.patch("/orders/1", Body.of("status", "paid"));
            api.delete("/orders/1");
            api.delete("/orders");
            api.mutatingGet("/orders/2/cancel", "/orders/2", Body.of("status", "cancelled"));
            api.report();
        });
        out.lines().forEach(line -> {
            if (!line.isEmpty()) {
                assertTrue(line.startsWith(Trace.PREFIX), "unexpected non-trace line: " + line);
            }
        });
    }
}
