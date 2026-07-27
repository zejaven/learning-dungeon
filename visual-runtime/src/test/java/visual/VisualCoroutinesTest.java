package visual;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualCoroutinesTest {

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
    void theRuntimeAnnouncesItsDispatchersBeforeAnyCoroutineExists() {
        String out = captureTrace(() -> VisualCoroutines.runtime(2, 4));
        assertTrue(out.contains("RUNTIME_READY"), "expected the dispatcher announcement, got:\n" + out);
        assertTrue(out.contains("\"name\":\"Default\""), "Default must exist, got:\n" + out);
        assertTrue(out.contains("\"name\":\"IO\""), "IO must exist, got:\n" + out);
        assertTrue(out.contains("\"threads\":7"), "2 + 4 + 1 worker threads, got:\n" + out);
    }

    @Test
    void aSuspendFunIsCompiledIntoAStateMachineClass() {
        String out = captureTrace(() -> VisualCoroutines.suspendFun("loadProfile")
                .local("userId", "42")
                .await("fetchUser(userId)", "user", "ada")
                .returns("Profile(ada)")
                .run());
        assertTrue(out.contains("SUSPEND_FUN_COMPILED"), "the transform must be shown, got:\n" + out);
        assertTrue(out.contains("\"className\":\"loadProfile$1\""),
                "the generated continuation class, got:\n" + out);
        assertTrue(out.contains("COROUTINE_SUSPENDED"), "the marker is the whole mechanism, got:\n" + out);
    }

    @Test
    void theStateMachineResumesAtTheLabelItWroteDownBeforeLeaving() {
        String out = captureTrace(() -> VisualCoroutines.suspendFun("loadProfile")
                .local("userId", "42")
                .await("fetchUser(userId)", "user", "ada")
                .await("fetchOrders(user.id)", "orders", "[o-17]")
                .returns("Profile(ada, 1 order)")
                .run());
        assertTrue(out.contains("STATE_MACHINE_STEP"), "the when(label) jump, got:\n" + out);
        assertTrue(out.contains("SUSPENDED"), "it must suspend, got:\n" + out);
        assertTrue(out.contains("RESUMED"), "and be resumed, got:\n" + out);
        assertTrue(out.contains("\"label\":1"), "resuming lands on the saved label, got:\n" + out);
        assertTrue(out.contains("\"name\":\"user\",\"value\":\"ada\""),
                "the result is stored in the continuation, got:\n" + out);
        assertTrue(out.contains("COROUTINE_COMPLETED"), "and finally return, got:\n" + out);
    }

    @Test
    void suspendingHandsTheThreadToTheNextCoroutineImmediately() {
        String out = captureTrace(() -> {
            VisualCoroutines rt = VisualCoroutines.runtime(1, 4);
            rt.launch("Default", "loadFeed");
            rt.launch("Default", "loadAvatar");
            rt.suspendAt("loadFeed", "delay(200)");
            rt.report();
        });
        assertTrue(out.contains("QUEUED"), "the second one has to wait, got:\n" + out);
        assertTrue(out.contains("SUSPENDED"), "the first one suspends, got:\n" + out);
        assertTrue(out.contains("\"name\":\"loadAvatar\",\"scope\":\"root\",\"kind\":\"LAUNCH\","
                        + "\"dispatcher\":\"Default\",\"state\":\"RUNNING\""),
                "the queued coroutine must get the freed thread, got:\n" + out);
        assertTrue(out.contains("worker threads 6"), "one Default worker, got:\n" + out);
    }

    @Test
    void aBlockingCallOnDispatchersDefaultStarvesThePool() {
        String out = captureTrace(() -> {
            VisualCoroutines rt = VisualCoroutines.runtime(2, 4);
            rt.launch("Default", "report-1");
            rt.launch("Default", "report-2");
            rt.launch("Default", "renderPrices");
            rt.blockingCall("report-1", "jdbc.executeQuery(...)");
            rt.blockingCall("report-2", "Thread.sleep(500)");
            rt.report();
        });
        assertTrue(out.contains("THREAD_BLOCKED"), "a blocking call must be visible, got:\n" + out);
        assertTrue(out.contains("POOL_STARVED"), "both workers are gone, got:\n" + out);
        assertTrue(out.contains("\"blocked\":true"), "the worker is marked blocked, got:\n" + out);
    }

    @Test
    void movingTheBlockingCallToDispatchersIoDoesNotStarveAnything() {
        String out = captureTrace(() -> {
            VisualCoroutines rt = VisualCoroutines.runtime(2, 4);
            rt.launch("Default", "report-3");
            rt.withContext("report-3", "IO", "jdbc.executeQuery(...)");
            rt.blockingCall("report-3", "jdbc.executeQuery(...)");
            rt.complete("report-3");
            rt.report();
        });
        assertTrue(out.contains("CONTEXT_SWITCHED"), "withContext must move it, got:\n" + out);
        assertTrue(out.contains("THREAD_BLOCKED"), "the call still blocks a thread, got:\n" + out);
        assertFalse(out.contains("POOL_STARVED"), "but IO can afford it, got:\n" + out);
        assertTrue(out.contains("context switches 1"), "counted once, got:\n" + out);
    }

    @Test
    void aScopeCannotReturnBeforeItsChildrenHaveFinished() {
        String out = captureTrace(() -> {
            VisualCoroutines rt = VisualCoroutines.runtime(2, 4);
            rt.scope("loadDashboard");
            rt.launchIn("loadDashboard", "IO", "loadUser");
            rt.launchIn("loadDashboard", "IO", "loadOrders");
            rt.suspendAt("loadUser", "httpGet(/user)");
            rt.joinScope("loadDashboard");
            rt.resume("loadUser");
            rt.complete("loadUser");
            rt.complete("loadOrders");
            rt.joinScope("loadDashboard");
        });
        assertTrue(out.contains("SCOPE_OPENED"), "the scope must be announced, got:\n" + out);
        assertTrue(out.contains("SCOPE_JOINED"), "the join must be visible, got:\n" + out);
        assertTrue(out.contains("loadUser (SUSPENDED)"), "it waits for the suspended child, got:\n" + out);
        assertTrue(out.contains("\"name\":\"loadDashboard\",\"kind\":\"COROUTINE_SCOPE\","
                        + "\"state\":\"COMPLETED\""),
                "the scope completes only after its children, got:\n" + out);
    }

    @Test
    void aFailingChildCancelsItsSiblingsAndFailsTheScope() {
        String out = captureTrace(() -> {
            VisualCoroutines rt = VisualCoroutines.runtime(2, 4);
            rt.scope("checkout");
            rt.launchIn("checkout", "IO", "chargeCard");
            rt.launchIn("checkout", "IO", "sendReceipt");
            rt.suspendAt("sendReceipt", "smtp.send(...)");
            rt.fail("chargeCard", "PaymentDeclined");
            rt.report();
        });
        assertTrue(out.contains("COROUTINE_FAILED"), "the child must fail, got:\n" + out);
        assertTrue(out.contains("CHILDREN_CANCELLED"), "the siblings must be cancelled, got:\n" + out);
        assertTrue(out.contains("\"name\":\"checkout\",\"kind\":\"COROUTINE_SCOPE\",\"state\":\"FAILED\""),
                "the scope fails with its child, got:\n" + out);
        assertTrue(out.contains("cancelled 1, failed 1"), "one of each, got:\n" + out);
    }

    @Test
    void aSupervisorScopeKeepsTheFailureLocal() {
        String out = captureTrace(() -> {
            VisualCoroutines rt = VisualCoroutines.runtime(2, 4);
            rt.supervisorScope("dashboard");
            rt.launchIn("dashboard", "IO", "widgetA");
            rt.launchIn("dashboard", "IO", "widgetB");
            rt.suspendAt("widgetB", "httpGet(/b)");
            rt.fail("widgetA", "TimeoutException");
            rt.report();
        });
        assertTrue(out.contains("SUPERVISOR_ISOLATED"), "the failure must stop there, got:\n" + out);
        assertFalse(out.contains("CHILDREN_CANCELLED"), "no sibling may be cancelled, got:\n" + out);
        assertTrue(out.contains("cancelled 0, failed 1"), "only the one failure, got:\n" + out);
    }

    @Test
    void cancellingASuspendedCoroutineTakesEffectAtOnce() {
        String out = captureTrace(() -> {
            VisualCoroutines rt = VisualCoroutines.runtime(2, 4);
            rt.launch("IO", "download");
            rt.suspendAt("download", "readChunk()");
            rt.cancel("download");
            rt.report();
        });
        assertTrue(out.contains("CANCELLATION_REQUESTED"), "cancel() only asks, got:\n" + out);
        assertTrue(out.contains("CANCELLED_AT_SUSPENSION_POINT"),
                "a suspended coroutine stops immediately, got:\n" + out);
        assertTrue(out.contains("cancelled 1"), "counted, got:\n" + out);
    }

    @Test
    void cancellingALoopWithNoSuspensionPointChangesNothing() {
        String out = captureTrace(() -> {
            VisualCoroutines rt = VisualCoroutines.runtime(2, 4);
            rt.launch("Default", "hashEverything");
            rt.cpuWork("hashEverything", "round 1 of 3");
            rt.cancel("hashEverything");
            rt.cpuWork("hashEverything", "round 2 of 3");
            rt.complete("hashEverything");
            rt.report();
        });
        assertTrue(out.contains("CPU_WORK"), "the loop runs, got:\n" + out);
        assertTrue(out.contains("CANCELLATION_IGNORED"), "and keeps running when cancelled, got:\n" + out);
        assertTrue(out.contains("cancelled 0"), "nothing was actually cancelled, got:\n" + out);
        assertTrue(out.contains("completed 1"), "it finished anyway, got:\n" + out);
    }

    @Test
    void ensureActiveMakesTheSameLoopCancellable() {
        String out = captureTrace(() -> {
            VisualCoroutines rt = VisualCoroutines.runtime(2, 4);
            rt.launch("Default", "hashProperly");
            rt.cooperativeCpuWork("hashProperly", "round 1 of 3");
            rt.cancel("hashProperly");
            rt.cooperativeCpuWork("hashProperly", "round 2 of 3");
            rt.report();
        });
        assertTrue(out.contains("CANCELLED_AT_SUSPENSION_POINT"), "ensureActive() throws, got:\n" + out);
        assertTrue(out.contains("cancelled 1"), "this one really stops, got:\n" + out);
        assertTrue(out.contains("completed 0"), "and never finishes, got:\n" + out);
    }

    @Test
    void globalScopeCoroutinesSurviveTheScopeThatShouldHaveOwnedThem() {
        String out = captureTrace(() -> {
            VisualCoroutines rt = VisualCoroutines.runtime(2, 4);
            rt.launchGlobal("Default", "pollMetrics");
            rt.scope("screen");
            rt.launchIn("screen", "IO", "loadScreen");
            rt.suspendAt("loadScreen", "httpGet(/screen)");
            rt.cancel("screen");
            rt.report();
        });
        assertTrue(out.contains("CHILDREN_CANCELLED"), "the scope's own child stops, got:\n" + out);
        assertTrue(out.contains("GLOBAL_SCOPE_LEAK"), "the orphan does not, got:\n" + out);
        assertTrue(out.contains("orphaned in GlobalScope 1"), "counted, got:\n" + out);
    }

    @Test
    void asyncStartsBothBeforeAnythingIsAwaited() {
        String out = captureTrace(() -> {
            VisualCoroutines rt = VisualCoroutines.runtime(1, 4);
            rt.scope("priceQuote");
            rt.launchIn("priceQuote", "Default", "quote");
            rt.async("priceQuote", "IO", "fetchRate");
            rt.async("priceQuote", "IO", "fetchFees");
            rt.suspendAt("fetchRate", "httpGet(/rate)");
            rt.suspendAt("fetchFees", "httpGet(/fees)");
            rt.await("quote", "fetchRate");
            rt.resume("fetchRate");
            rt.complete("fetchRate");
            rt.report();
        });
        assertTrue(out.contains("ASYNC_STARTED"), "async must be visible, got:\n" + out);
        assertTrue(out.contains("\"kind\":\"ASYNC\""), "the Deferred is a coroutine too, got:\n" + out);
        assertTrue(out.contains("\"awaiting\":\"fetchRate\""), "the caller waits for it, got:\n" + out);
        assertTrue(out.contains("RESUMED"), "completing the Deferred resumes the awaiter, got:\n" + out);
    }

    @Test
    void theScaleTablePricesThreeWaysOfRunningTheSameTasks() {
        String out = captureTrace(() -> VisualCoroutines.compareScale(100_000));
        assertTrue(out.contains("SCALE_COMPARED"), "expected the comparison, got:\n" + out);
        assertTrue(out.contains("\"model\":\"PLATFORM_THREADS\",\"count\":100000,\"memoryMb\":100000,"
                        + "\"osThreads\":100000,\"keyword\":\"none\",\"feasible\":false"),
                "100k threads is not a thing, got:\n" + out);
        assertTrue(out.contains("\"model\":\"COROUTINES\",\"count\":100000,\"memoryMb\":19,"
                        + "\"osThreads\":4,\"keyword\":\"suspend\",\"feasible\":true"),
                "100k coroutines fit on four threads, got:\n" + out);
        assertTrue(out.contains("\"model\":\"VIRTUAL_THREADS\",\"count\":100000,\"memoryMb\":98,"
                        + "\"osThreads\":4,\"keyword\":\"none\",\"feasible\":true"),
                "virtual threads get there without a keyword, got:\n" + out);
    }

    @Test
    void everyTraceLineIsPrefixed() {
        String out = captureTrace(() -> {
            VisualCoroutines.suspendFun("loadProfile")
                    .local("userId", "42")
                    .await("fetchUser(userId)", "user", "ada")
                    .returns("Profile(ada)")
                    .run();

            VisualCoroutines rt = VisualCoroutines.runtime(2, 4);
            rt.scope("dashboard");
            rt.launchIn("dashboard", "Default", "a");
            rt.launchIn("dashboard", "Default", "b");
            rt.launchIn("dashboard", "Default", "c");
            rt.async("dashboard", "IO", "d");
            rt.launchGlobal("Default", "metrics");
            rt.suspendAt("a", "delay(1)");
            rt.resume("a");
            rt.withContext("a", "IO", "readFile()");
            rt.blockingCall("a", "File.readBytes()");
            rt.complete("a");
            rt.cpuWork("b", "round 1");
            rt.cooperativeCpuWork("b", "round 2");
            rt.suspendAt("d", "httpGet(/d)");
            rt.await("b", "d");
            rt.resume("d");
            rt.complete("d");
            rt.cancel("c");
            rt.joinScope("dashboard");
            rt.fail("b", "IllegalStateException");
            rt.cancel("dashboard");
            rt.report();

            VisualCoroutines.compareScale(1_000);
        });
        out.lines().forEach(line -> {
            if (!line.isEmpty()) {
                assertTrue(line.startsWith(Trace.PREFIX), "unexpected non-trace line: " + line);
            }
        });
    }
}
