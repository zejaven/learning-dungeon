package visual;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualLoadBalancerTest {

    private static final String SERVICE = "POST /checkout";

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
    void aSingleServerAnnouncesTheThroughputItsSlotsAllow() {
        String out = captureTrace(() -> VisualLoadBalancer.singleServer(SERVICE, 4, 2, 12));
        assertTrue(out.contains("SERVER_STARTED"), "expected the topology, got:\n" + out);
        assertTrue(out.contains("\"topology\":\"SINGLE\""), "one server is SINGLE, got:\n" + out);
        assertTrue(out.contains("\"capacityPerTick\":2.0"),
                "4 slots over 2 ticks is 2 per tick, got:\n" + out);
    }

    @Test
    void demandBelowCapacityLeavesTheQueueEmptyAndLatencyAtServiceTime() {
        String out = captureTrace(() -> {
            VisualLoadBalancer server = VisualLoadBalancer.singleServer(SERVICE, 4, 2, 12);
            server.traffic(2, 8);
            server.tick(3);
            server.report();
        });
        assertFalse(out.contains("QUEUE_GROWING"), "nothing should queue, got:\n" + out);
        assertFalse(out.contains("REQUESTS_REJECTED"), "nothing should be refused, got:\n" + out);
        assertTrue(out.contains("arrived 16, served 16, rejected 0"),
                "every request must be served, got:\n" + out);
        assertTrue(out.contains("Average latency 2.0 tick(s)"),
                "latency is just the work, got:\n" + out);
    }

    @Test
    void demandAboveCapacityBecomesQueueThenLatencyThenRejections() {
        String out = captureTrace(() -> {
            VisualLoadBalancer server = VisualLoadBalancer.singleServer(SERVICE, 4, 2, 12);
            server.clientTimeout(4);
            server.traffic(6, 10);
            server.report();
        });
        assertTrue(out.contains("QUEUE_GROWING"), "the surplus must queue, got:\n" + out);
        assertTrue(out.contains("LATENCY_CLIMBING"), "queueing is latency, got:\n" + out);
        assertTrue(out.contains("REQUESTS_REJECTED"), "a bounded queue must refuse, got:\n" + out);
        assertTrue(out.contains("CLIENT_TIMEOUT"), "clients must give up, got:\n" + out);
        assertTrue(out.contains("WASTED_WORK"), "and be answered anyway, got:\n" + out);
    }

    @Test
    void aBiggerBoxRaisesTheCeilingAndKeepsTheSingleFailureDomain() {
        String out = captureTrace(() -> {
            VisualLoadBalancer server = VisualLoadBalancer.singleServer(SERVICE, 4, 2, 12);
            server.traffic(6, 6);
            server.scaleUp(12);
            server.traffic(4, 10);
            server.failNode("app-1");
        });
        assertTrue(out.contains("SCALED_UP"), "vertical scaling must be visible, got:\n" + out);
        assertTrue(out.contains("capacity goes from 2.0 to 6.0"),
                "three times the slots is three times the throughput, got:\n" + out);
        assertTrue(out.contains("QUEUE_DRAINED"), "the backlog must clear, got:\n" + out);
        assertTrue(out.contains("OUTAGE"), "one box dying is the whole service, got:\n" + out);
    }

    @Test
    void addingReplicasPutsABalancerInFrontAndDrainsTheBacklog() {
        String out = captureTrace(() -> {
            VisualLoadBalancer service = VisualLoadBalancer.singleServer(SERVICE, 4, 2, 12);
            service.traffic(6, 6);
            service.addNode();
            service.addNode();
            service.traffic(4, 8);
            service.tick(10);
            service.report();
        });
        assertTrue(out.contains("BALANCER_STARTED"), "a balancer must appear, got:\n" + out);
        assertTrue(out.contains("NODE_ADDED"), "replicas must join, got:\n" + out);
        assertTrue(out.contains("capacity 4.0 → 6.0"), "three replicas do 6 per tick, got:\n" + out);
        assertTrue(out.contains("QUEUE_DRAINED"), "the backlog must clear, got:\n" + out);
        assertTrue(out.contains("\"topology\":\"BALANCED\""), "the shape changed, got:\n" + out);
    }

    @Test
    void roundRobinKeepsFeedingADegradedNodeUntilItRejects() {
        String out = captureTrace(() -> {
            VisualLoadBalancer pool = VisualLoadBalancer.behindBalancer(SERVICE, 3, 4, 2, 6);
            pool.degrade("app-2", 8);
            pool.traffic(4, 16);
            pool.report();
        });
        assertTrue(out.contains("NODE_DEGRADED"), "a node must go slow, got:\n" + out);
        assertTrue(out.contains("HOTSPOT"), "one node queues while others idle, got:\n" + out);
        assertTrue(out.contains("REQUESTS_REJECTED"), "the slow node must refuse, got:\n" + out);
    }

    @Test
    void leastBusyRoutesAroundTheSameDegradedNode() {
        String out = captureTrace(() -> {
            VisualLoadBalancer pool = VisualLoadBalancer.behindBalancer(SERVICE, 3, 4, 2, 6);
            pool.strategy(VisualLoadBalancer.Strategy.LEAST_BUSY);
            pool.degrade("app-2", 8);
            pool.traffic(4, 16);
            pool.report();
        });
        assertTrue(out.contains("STRATEGY_CHANGED"), "the algorithm must change, got:\n" + out);
        assertFalse(out.contains("REQUESTS_REJECTED"),
                "routing by load must avoid the rejections, got:\n" + out);
    }

    @Test
    void withoutHealthChecksTheBalancerKeepsRoutingToADeadReplica() {
        String out = captureTrace(() -> {
            VisualLoadBalancer pool = VisualLoadBalancer.behindBalancer(SERVICE, 3, 4, 2, 6);
            pool.traffic(3, 4);
            pool.failNode("app-2");
            pool.traffic(3, 6);
            pool.report();
        });
        assertTrue(out.contains("NODE_FAILED"), "the node must die, got:\n" + out);
        assertTrue(out.contains("BLACKHOLED"), "traffic must keep going to it, got:\n" + out);
        assertFalse(out.contains("NODE_EVICTED"), "nobody is checking, got:\n" + out);
    }

    @Test
    void healthChecksEvictTheDeadReplicaAndHandItsShareToTheSurvivors() {
        String out = captureTrace(() -> {
            VisualLoadBalancer pool = VisualLoadBalancer.behindBalancer(SERVICE, 3, 4, 2, 6);
            pool.healthChecks(2);
            pool.traffic(5, 4);
            pool.failNode("app-2");
            pool.traffic(5, 8);
            pool.report();
        });
        assertTrue(out.contains("HEALTH_CHECK_FAILED"), "the probe must fail, got:\n" + out);
        assertTrue(out.contains("NODE_EVICTED"), "the corpse must leave the rotation, got:\n" + out);
        assertTrue(out.contains("LOAD_REDISTRIBUTED"), "survivors inherit the share, got:\n" + out);
        assertTrue(out.contains("BLACKHOLED"),
                "the detection window still costs real requests, got:\n" + out);
    }

    @Test
    void inMemorySessionsBreakAsSoonAsThereIsMoreThanOneReplica() {
        String out = captureTrace(() -> {
            VisualLoadBalancer pool = VisualLoadBalancer.behindBalancer("GET /cart", 3, 4, 2, 6);
            pool.sessionsInMemory();
            pool.round("alice", "bob");
            pool.round("alice", "bob");
            pool.report();
        });
        assertTrue(out.contains("STATE_IS_LOCAL"), "state must be declared local, got:\n" + out);
        assertTrue(out.contains("SESSION_MISS"), "a request must miss its session, got:\n" + out);
    }

    @Test
    void stickyRoutingRemovesTheMissesAndLosesTheSessionsWithTheNode() {
        String out = captureTrace(() -> {
            VisualLoadBalancer pool = VisualLoadBalancer.behindBalancer("GET /cart", 3, 4, 2, 6);
            pool.sessionsInMemory();
            pool.strategy(VisualLoadBalancer.Strategy.STICKY);
            pool.round("alice", "bob");
            pool.round("alice", "bob");
            pool.failNode("app-1");
            pool.report();
        });
        assertTrue(out.contains("STICKY_ROUTED"), "clients must be pinned, got:\n" + out);
        assertFalse(out.contains("SESSION_MISS"), "pinning removes the misses, got:\n" + out);
        assertTrue(out.contains("SESSIONS_LOST"), "the pinned state dies with the node, got:\n" + out);
    }

    @Test
    void aSharedDependencyCapsTheWholeFleetNoMatterHowManyReplicas() {
        String out = captureTrace(() -> {
            VisualLoadBalancer pool = VisualLoadBalancer.behindBalancer(SERVICE, 3, 4, 2, 6);
            pool.sharedDependency("one PostgreSQL primary", 3);
            pool.traffic(6, 10);
            pool.addNode();
            pool.traffic(6, 10);
            pool.report();
        });
        assertTrue(out.contains("BOTTLENECK_SATURATED"), "the shared limit must bite, got:\n" + out);
        assertTrue(out.contains("\"capacityPerTick\":3.0"),
                "four replicas cannot exceed the database, got:\n" + out);
        assertTrue(out.contains("REQUESTS_REJECTED"), "the surplus is still refused, got:\n" + out);
    }

    @Test
    void theComparisonPricesTheSameTrafficAcrossFourTopologies() {
        String out = captureTrace(VisualLoadBalancer::compare);
        assertTrue(out.contains("TOPOLOGIES_COMPARED"), "expected the table, got:\n" + out);
        assertTrue(out.contains("\"topology\":\"SINGLE\",\"nodes\":1,\"capacityPerTick\":2.0"),
                "the single server does 2 per tick, got:\n" + out);
        assertTrue(out.contains("\"topology\":\"BIGGER_BOX\",\"nodes\":1,\"capacityPerTick\":6.0"),
                "the bigger box does 6 per tick, got:\n" + out);
        assertTrue(out.contains("\"topology\":\"THREE_NODES\",\"nodes\":3,\"capacityPerTick\":6.0"),
                "three replicas also do 6 per tick, got:\n" + out);
        assertTrue(out.contains("\"topology\":\"THREE_NODES_ONE_DB\",\"nodes\":3,\"capacityPerTick\":3.0"),
                "the shared database caps the fleet, got:\n" + out);
        assertTrue(out.contains("\"survivesLoss\":false"), "one box does not survive, got:\n" + out);
        assertTrue(out.contains("\"survivesLoss\":true"), "three replicas do, got:\n" + out);
    }

    @Test
    void everyTraceLineIsPrefixed() {
        String out = captureTrace(() -> {
            VisualLoadBalancer single = VisualLoadBalancer.singleServer(SERVICE, 4, 2, 12);
            single.clientTimeout(4);
            single.traffic(6, 6);
            single.burst(10);
            single.tick(4);
            single.scaleUp(12);
            single.traffic(4, 4);
            single.addNode();
            single.traffic(6, 4);
            single.degrade("app-2", 6);
            single.traffic(4, 4);
            single.failNode("app-1");
            single.failNode("app-2");
            single.report();

            VisualLoadBalancer pool = VisualLoadBalancer.behindBalancer("GET /cart", 3, 4, 2, 6);
            pool.healthChecks(2).sessionsInMemory().strategy(VisualLoadBalancer.Strategy.STICKY);
            pool.round("alice", "bob");
            pool.failNode("app-3");
            pool.round("alice", "bob");
            pool.sharedDependency("one PostgreSQL primary", 2);
            pool.traffic(6, 6);
            pool.report();

            VisualLoadBalancer.compare();
        });
        out.lines().forEach(line -> {
            if (!line.isEmpty()) {
                assertTrue(line.startsWith(Trace.PREFIX), "unexpected non-trace line: " + line);
            }
        });
    }
}
