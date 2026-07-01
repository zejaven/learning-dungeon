package visual;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualNPlusOneTest {

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

    private long count(String out, String event) {
        return out.lines().filter(l -> l.contains("\"event\":\"" + event + "\"")).count();
    }

    @Test
    void lazyAccessFiresOneQueryPerParent() {
        // 1 root query + 3 lazy selects = the classic N+1 (here 1 + 3).
        String out = captureTrace(() -> {
            VisualNPlusOne db = new VisualNPlusOne("shop");
            db.loadParents("orders", "lines", 3, 2);
            db.accessAllChildren();
        });
        assertEquals(1, count(out, "ROOT_QUERY"));
        assertEquals(3, count(out, "N_PLUS_ONE_QUERY"));
    }

    @Test
    void joinFetchLoadsEverythingInOneQuery() {
        String out = captureTrace(() -> {
            VisualNPlusOne db = new VisualNPlusOne("shop");
            db.loadParentsWithJoinFetch("orders", "lines", 3, 2);
            db.accessAllChildren();
        });
        assertEquals(1, count(out, "JOIN_FETCH_QUERY"));
        assertEquals(0, count(out, "N_PLUS_ONE_QUERY"));
        // Children were already fetched, so touching them costs nothing.
        assertTrue(out.contains("COLLECTION_ALREADY_LOADED"));
    }

    @Test
    void entityGraphAlsoUsesASingleQuery() {
        String out = captureTrace(() -> {
            VisualNPlusOne db = new VisualNPlusOne("shop");
            db.loadParentsWithEntityGraph("orders", "lines", 3, 2);
        });
        assertEquals(1, count(out, "ENTITY_GRAPH_QUERY"));
    }

    @Test
    void batchSizeGroupsLazyChildrenIntoFewerQueries() {
        // 5 parents, batch size 2 -> ceil(5/2) = 3 batch queries instead of 5.
        String out = captureTrace(() -> {
            VisualNPlusOne db = new VisualNPlusOne("shop");
            db.setBatchSize(2);
            db.loadParents("orders", "lines", 5, 2);
            db.accessAllChildren();
        });
        assertEquals(3, count(out, "BATCH_QUERY"));
        assertEquals(0, count(out, "N_PLUS_ONE_QUERY"));
    }

    @Test
    void everyTraceLineIsPrefixed() {
        String out = captureTrace(() -> {
            VisualNPlusOne db = new VisualNPlusOne("shop");
            db.loadParents("orders", "lines", 2, 1);
            db.accessAllChildren();
        });
        out.lines().forEach(line -> {
            if (!line.isEmpty()) {
                assertTrue(line.startsWith(Trace.PREFIX),
                        "unexpected non-trace line: " + line);
            }
        });
    }
}
