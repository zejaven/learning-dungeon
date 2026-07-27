package visual;

import org.junit.jupiter.api.Test;
import visual.VisualSharedState.Source;
import visual.VisualSharedState.Store;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualSharedStateTest {

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
    void bothDecisionsAreAnnouncedBeforeAnythingConnects() {
        String out = captureTrace(() -> VisualSharedState.server(Store.CONCURRENT_MAP, Source.ATOMIC_COUNTER));
        assertTrue(out.contains("REGISTRY_READY"), "expected the announcement, got:\n" + out);
        assertTrue(out.contains("\"store\":\"CONCURRENT_MAP\",\"valueSource\":\"ATOMIC_COUNTER\""),
                "store and value source must be in the state, got:\n" + out);
    }

    @Test
    void everyConnectionGetsItsOwnThread() {
        String out = captureTrace(() -> {
            VisualSharedState server = VisualSharedState.server(Store.CONCURRENT_MAP, Source.ATOMIC_COUNTER);
            server.connect("alice");
            server.connect("bob");
        });
        assertTrue(out.contains("CONNECTION_OPENED"), "expected the open event, got:\n" + out);
        assertTrue(out.contains("\"thread\":\"ws-1\""), "the first connection is served by ws-1, got:\n" + out);
        assertTrue(out.contains("\"thread\":\"ws-2\""), "the second one by ws-2, got:\n" + out);
    }

    @Test
    void aRegistryInAnInstanceFieldReachesOnlyItsOwnConnection() {
        String out = captureTrace(() -> {
            VisualSharedState server = VisualSharedState.server(Store.INSTANCE_FIELD, Source.ATOMIC_COUNTER);
            server.connect("alice");
            server.connect("bob");
            server.join("alice");
            server.join("bob");
            server.broadcast("market open");
            server.report();
        });
        assertTrue(out.contains("REGISTRY_PRIVATE"), "the map is private per connection, got:\n" + out);
        assertTrue(out.contains("ChatHandler#2"), "each connection gets its own object, got:\n" + out);
        assertTrue(out.contains("written to 1 socket(s)"), "the fan-out reaches one, got:\n" + out);
        assertTrue(out.contains("BROADCAST_MISSED"), "the other one must be missed, got:\n" + out);
        assertTrue(out.contains("connections missed 1"), "counted once, got:\n" + out);
    }

    @Test
    void twoThreadsInAPlainHashMapLoseAnEntryAndReuseAValue() {
        String out = captureTrace(() -> {
            VisualSharedState server = VisualSharedState.server(Store.PLAIN_MAP, Source.PLAIN_COUNTER);
            server.connect("alice");
            server.connect("bob");
            server.beginJoin("alice");
            server.beginJoin("bob");
            server.finishJoin("alice");
            server.finishJoin("bob");
            server.report();
        });
        assertTrue(out.contains("VALUE_READ"), "nextId++ starts with a read, got:\n" + out);
        assertTrue(out.contains("DUPLICATE_VALUE"), "both threads read the same 0, got:\n" + out);
        assertTrue(out.contains("ENTRY_LOST"), "an unsynchronized put must lose an entry, got:\n" + out);
        assertTrue(out.contains("duplicate values 1"), "one collision, got:\n" + out);
        assertTrue(out.contains("entries lost to a data race 1"), "one lost entry, got:\n" + out);
        // The lost update is visible in the counter itself: two connections, still 1.
        assertTrue(out.contains("\"counter\":1"), "the counter must have lost an increment, got:\n" + out);
    }

    @Test
    void aThreadSafeMapDoesNotMakeReadThenWriteSafe() {
        String out = captureTrace(() -> {
            VisualSharedState server = VisualSharedState.server(Store.SYNCHRONIZED_MAP, Source.REGISTRY_SIZE);
            server.connect("alice");
            server.connect("bob");
            server.beginJoin("alice");
            server.beginJoin("bob");
            server.finishJoin("alice");
            server.finishJoin("bob");
            server.report();
        });
        assertTrue(out.contains("CHECK_THEN_ACT"), "the compound action is the bug, got:\n" + out);
        assertTrue(out.contains("DUPLICATE_VALUE"), "size()+1 collides, got:\n" + out);
        assertFalse(out.contains("ENTRY_LOST"), "a synchronized map cannot lose an entry, got:\n" + out);
    }

    @Test
    void sizePlusOneReissuesValuesAfterADisconnectWithNoRaceAtAll() {
        String out = captureTrace(() -> {
            VisualSharedState server = VisualSharedState.server(Store.CONCURRENT_MAP, Source.REGISTRY_SIZE);
            server.connect("alice");
            server.connect("bob");
            server.connect("carol");
            server.join("alice");
            server.join("bob");
            server.disconnect("alice");
            server.join("carol");
            server.report();
        });
        assertTrue(out.contains("UNREGISTERED"), "the close callback must remove it, got:\n" + out);
        assertTrue(out.contains("DUPLICATE_VALUE"), "the shrinking size reissues 2, got:\n" + out);
        assertTrue(out.contains("values handed out 3, distinct values 2"),
                "three connections, two distinct values, got:\n" + out);
    }

    @Test
    void iteratingASynchronizedMapWhileSomeoneRegistersIsTheClassicTrap() {
        String out = captureTrace(() -> {
            VisualSharedState server = VisualSharedState.server(Store.SYNCHRONIZED_MAP, Source.ATOMIC_COUNTER);
            server.connect("alice");
            server.connect("bob");
            server.join("alice");
            server.beginJoin("bob");
            server.broadcast("market open");
            server.finishJoin("bob");
        });
        assertTrue(out.contains("ITERATION_RACE"), "traversal is not covered by the lock, got:\n" + out);
        assertTrue(out.contains("ConcurrentModificationException"),
                "that is what it throws, got:\n" + out);
    }

    @Test
    void aConcurrentMapWithAnAtomicCounterSurvivesTheSameInterleaving() {
        String out = captureTrace(() -> {
            VisualSharedState server = VisualSharedState.server(Store.CONCURRENT_MAP, Source.ATOMIC_COUNTER);
            server.connect("alice");
            server.connect("bob");
            server.beginJoin("alice");
            server.beginJoin("bob");
            server.finishJoin("alice");
            server.finishJoin("bob");
            server.broadcast("market open");
            server.report();
        });
        assertTrue(out.contains("VALUE_ISSUED"), "incrementAndGet issues immediately, got:\n" + out);
        assertFalse(out.contains("DUPLICATE_VALUE"), "nothing may collide, got:\n" + out);
        assertFalse(out.contains("ENTRY_LOST"), "nothing may be lost, got:\n" + out);
        assertFalse(out.contains("BROADCAST_MISSED"), "everybody must be reached, got:\n" + out);
        assertTrue(out.contains("values handed out 2, distinct values 2, duplicate values 0"),
                "two connections, two distinct values, got:\n" + out);
    }

    @Test
    void aSessionLeftInTheRegistryIsAMemoryLeakAndADeadWrite() {
        String out = captureTrace(() -> {
            VisualSharedState server = VisualSharedState.server(Store.CONCURRENT_MAP, Source.ATOMIC_COUNTER);
            server.connect("alice");
            server.connect("bob");
            server.join("alice");
            server.join("bob");
            server.disconnectWithoutCleanup("bob");
            server.broadcast("market open");
            server.report();
        });
        assertTrue(out.contains("REGISTRY_LEAK"), "the entry outlived its socket, got:\n" + out);
        assertTrue(out.contains("entries leaked after close 1"), "counted once, got:\n" + out);
        assertTrue(out.contains("writes into a dead entry 1"), "the broadcast hits it, got:\n" + out);
    }

    @Test
    void aConcurrentRegistryDoesNotMakeOneSessionSafeToWriteTwice() {
        String unsafe = captureTrace(() -> {
            VisualSharedState server = VisualSharedState.server(Store.CONCURRENT_MAP, Source.ATOMIC_COUNTER);
            server.connect("alice");
            server.join("alice");
            server.sendFromTwoThreads("alice", "price 42", "price 43");
            server.report();
        });
        assertTrue(unsafe.contains("CONCURRENT_WRITE_FAILED"), "two writers, one session, got:\n" + unsafe);
        assertTrue(unsafe.contains("TEXT_PARTIAL_WRITING"), "that is the exception, got:\n" + unsafe);
        assertTrue(unsafe.contains("concurrent-write failures 1"), "counted once, got:\n" + unsafe);

        String safe = captureTrace(() -> {
            VisualSharedState server = VisualSharedState
                    .server(Store.CONCURRENT_MAP, Source.ATOMIC_COUNTER)
                    .serializeWrites();
            server.connect("alice");
            server.join("alice");
            server.sendFromTwoThreads("alice", "price 42", "price 43");
            server.report();
        });
        assertTrue(safe.contains("WRITES_SERIALIZED"), "the decorator must be announced, got:\n" + safe);
        assertTrue(safe.contains("WRITE_SERIALIZED"), "the second write waits, got:\n" + safe);
        assertTrue(safe.contains("concurrent-write failures 0"), "nothing may fail, got:\n" + safe);
    }

    @Test
    void anAtomicCounterIsUniquePerJvmAndTwoJvmsBothStartAtOne() {
        String out = captureTrace(() -> {
            VisualSharedState servers = VisualSharedState
                    .cluster(Store.CONCURRENT_MAP, Source.ATOMIC_COUNTER, 2);
            servers.connect("alice");
            servers.connect("bob");
            servers.join("alice");
            servers.join("bob");
            servers.broadcast("market open");
            servers.report();
        });
        assertTrue(out.contains("CROSS_NODE_DUPLICATE"), "both nodes hand out 1, got:\n" + out);
        assertFalse(out.contains("\"event\":\"DUPLICATE_VALUE\""),
                "the clash is across nodes, not inside one, got:\n" + out);
        assertTrue(out.contains("BROADCAST_MISSED"), "a node cannot reach another node's socket, got:\n" + out);
        assertTrue(out.contains("duplicate values 1"), "one duplicate, got:\n" + out);
    }

    @Test
    void aSharedSequenceAndARandomUuidAreUniqueAcrossNodes() {
        String sequence = captureTrace(() -> {
            VisualSharedState servers = VisualSharedState
                    .cluster(Store.CONCURRENT_MAP, Source.DB_SEQUENCE, 2);
            servers.connect("alice");
            servers.connect("bob");
            servers.join("alice");
            servers.join("bob");
            servers.report();
        });
        assertTrue(sequence.contains("\"value\":\"1001\""), "the sequence starts at 1001, got:\n" + sequence);
        assertTrue(sequence.contains("\"value\":\"1002\""), "and never repeats, got:\n" + sequence);
        assertTrue(sequence.contains("duplicate values 0"), "no duplicates, got:\n" + sequence);

        String uuid = captureTrace(() -> {
            VisualSharedState servers = VisualSharedState
                    .cluster(Store.CONCURRENT_MAP, Source.RANDOM_UUID, 2);
            servers.connect("alice");
            servers.connect("bob");
            servers.join("alice");
            servers.join("bob");
            servers.report();
        });
        assertTrue(uuid.contains("values handed out 2, distinct values 2, duplicate values 0"),
                "no coordination and still unique, got:\n" + uuid);
    }

    @Test
    void theComparisonSeparatesThreadSafeFromGloballyUnique() {
        String out = captureTrace(VisualSharedState::compareValueSources);
        assertTrue(out.contains("VALUE_SOURCES_COMPARED"), "expected the comparison, got:\n" + out);
        assertTrue(out.contains("\"source\":\"ATOMIC_COUNTER\",\"uniqueInJvm\":true,\"uniqueInCluster\":false"),
                "an AtomicLong stops at the JVM edge, got:\n" + out);
        assertTrue(out.contains("\"source\":\"RANDOM_UUID\",\"uniqueInJvm\":true,\"uniqueInCluster\":true"),
                "a UUID needs no coordination, got:\n" + out);
        assertTrue(out.contains("\"source\":\"DB_SEQUENCE\",\"uniqueInJvm\":true,\"uniqueInCluster\":true,"
                        + "\"ordered\":true,\"coordination\":\"DATABASE\""),
                "a sequence buys order with a round trip, got:\n" + out);
    }

    @Test
    void everyTraceLineIsPrefixed() {
        String out = captureTrace(() -> {
            VisualSharedState unsafe = VisualSharedState.server(Store.PLAIN_MAP, Source.PLAIN_COUNTER);
            unsafe.connect("alice");
            unsafe.connect("bob");
            unsafe.beginJoin("alice");
            unsafe.beginJoin("bob");
            unsafe.finishJoin("alice");
            unsafe.finishJoin("bob");
            unsafe.broadcast("market open");
            unsafe.report();

            VisualSharedState safe = VisualSharedState
                    .server(Store.CONCURRENT_MAP, Source.ATOMIC_COUNTER)
                    .serializeWrites();
            safe.connect("alice");
            safe.connect("bob");
            safe.join("alice");
            safe.join("bob");
            safe.sendFromTwoThreads("alice", "price 42", "price 43");
            safe.broadcast("market open");
            safe.disconnect("alice");
            safe.disconnectWithoutCleanup("bob");
            safe.report();

            VisualSharedState.cluster(Store.SYNCHRONIZED_MAP, Source.RANDOM_UUID, 2).report();
            VisualSharedState.compareValueSources();
        });
        out.lines().forEach(line -> {
            if (!line.isEmpty()) {
                assertTrue(line.startsWith(Trace.PREFIX), "unexpected non-trace line: " + line);
            }
        });
    }
}
