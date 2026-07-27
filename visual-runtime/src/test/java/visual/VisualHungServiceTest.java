package visual;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualHungServiceTest {

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

    private static VisualHungService incident() {
        return VisualHungService.alarm("payments-api", "every request times out; the access log stopped 4 minutes ago");
    }

    @Test
    void anAlarmKnowsTheSymptomAndNothingElse() {
        String out = captureTrace(VisualHungServiceTest::incident);
        assertTrue(out.contains("SERVICE_UNRESPONSIVE"), "expected the opening event, got:\n" + out);
        assertTrue(out.contains("\"stage\":\"alarm\""), "the incident starts at the alarm, got:\n" + out);
        assertTrue(out.contains("\"failureMode\":\"unknown\""), "the failure mode is not known yet, got:\n" + out);
        assertTrue(out.contains("\"rootCause\":null"), "no cause at the alarm, got:\n" + out);
    }

    @Test
    void aRefusedConnectionClassifiesAsNothingListening() {
        String out = captureTrace(() -> {
            VisualHungService incident = incident();
            incident.probe("tcp", "nc -vz payments 8080", "refused", "Connection refused");
            incident.classify();
        });
        assertTrue(out.contains("LAYER_PROBED"), "expected the probe event, got:\n" + out);
        assertTrue(out.contains("FAILURE_CLASSIFIED"), "expected the classification event, got:\n" + out);
        assertTrue(out.contains("\"failureMode\":\"not-listening\""), "a refusal means nothing is listening, got:\n" + out);
        assertTrue(out.contains("OOMKilled"), "the not-listening branch must point at the platform, got:\n" + out);
    }

    @Test
    void acceptedButSilentIsTheThreadDumpCase() {
        String out = captureTrace(() -> {
            VisualHungService incident = incident();
            incident.probe("tcp", "nc -vz payments 8080", "ok", "connected");
            incident.probe("endpoint", "curl --max-time 5 /api/payments/42", "timeout", "no bytes after 5s");
            incident.classify();
        });
        assertTrue(out.contains("\"failureMode\":\"accepting-but-silent\""),
                "connected but silent must classify as accepting-but-silent, got:\n" + out);
    }

    @Test
    void aGreenHealthCheckInFrontOfAHangingEndpointIsItsOwnFailureMode() {
        String out = captureTrace(() -> {
            VisualHungService incident = incident();
            incident.probe("tcp", "nc -vz payments 8080", "ok", "connected");
            incident.probe("health", "curl /actuator/health", "ok", "200 UP in 3ms");
            incident.probe("endpoint", "curl --max-time 5 /api/payments/42", "timeout", "no bytes after 5s");
            incident.classify();
        });
        assertTrue(out.contains("\"failureMode\":\"healthy-but-hanging\""),
                "a green health check plus a hanging endpoint is its own mode, got:\n" + out);
        assertTrue(out.contains("readiness"), "the lesson must separate readiness from liveness, got:\n" + out);
    }

    @Test
    void restartingFirstIsRecordedAndMakesEveryLaterCaptureUseless() {
        String out = captureTrace(() -> {
            VisualHungService incident = incident();
            incident.restartFirst("kubectl rollout restart deploy/payments-api");
            incident.capture("a thread dump", "jcmd <pid> Thread.print");
            incident.review();
        });
        assertTrue(out.contains("RESTARTED_BLIND"), "expected the reflex-restart event, got:\n" + out);
        assertTrue(out.contains("EVIDENCE_LOST"), "a capture after a restart must be reported as lost, got:\n" + out);
        assertTrue(out.contains("restarted-blind"), "the restart must be recorded as a misstep, got:\n" + out);
        assertTrue(out.contains("evidence-lost"), "the lost evidence must be recorded as a misstep, got:\n" + out);
        assertTrue(out.contains("\"lost\":true"), "the artifact itself must be marked lost, got:\n" + out);
    }

    @Test
    void capturingBeforeRestartingKeepsTheEvidence() {
        String out = captureTrace(() -> {
            VisualHungService incident = incident();
            incident.capture("3 thread dumps, 5s apart", "jcmd <pid> Thread.print");
            incident.restore("restart 2 of 3 pods, keep one hung pod out of the load balancer",
                    "traffic recovers and one hung process survives for study");
        });
        assertTrue(out.contains("EVIDENCE_CAPTURED"), "expected the capture event, got:\n" + out);
        assertTrue(out.contains("SERVICE_RESTORED"), "expected the mitigation event, got:\n" + out);
        assertTrue(out.contains("\"lost\":false"), "evidence taken before a restart survives, got:\n" + out);
        assertFalse(out.contains("evidence-lost"), "nothing was lost here, got:\n" + out);
    }

    @Test
    void theBiggestThreadGroupIsTheOutageAndItsShareIsComputed() {
        String out = captureTrace(() -> {
            VisualHungService incident = incident();
            incident.threads("http-nio-8080-exec", 198, "RUNNABLE",
                    "java.net.SocketInputStream.socketRead0 <- PaymentsClient.charge");
            incident.threads("idle workers", 2, "WAITING", "ThreadPoolExecutor.getTask");
        });
        assertTrue(out.contains("THREADS_READ"), "expected the thread-dump event, got:\n" + out);
        assertTrue(out.contains("\"share\":99"), "198 of 200 threads must be reported as 99%, got:\n" + out);
        assertTrue(out.contains("\"threadTotal\":200"), "the dump total must be in the state, got:\n" + out);
    }

    @Test
    void aSaturatedPoolIsSeparatedFromAHealthyOne() {
        String out = captureTrace(() -> {
            VisualHungService incident = incident();
            incident.pool("the Tomcat worker pool", 200, 200, 1500);
            incident.pool("the HikariCP connection pool", 3, 20, 0);
        });
        assertTrue(out.contains("POOL_READ"), "expected the pool event, got:\n" + out);
        assertTrue(out.contains("\"saturated\":true"), "a full pool must be flagged, got:\n" + out);
        assertTrue(out.contains("\"saturated\":false"), "a pool with room must not be, got:\n" + out);
    }

    @Test
    void gcThrashIsOnlyCalledWhenPausesDominateAndTheHeapStaysFull() {
        String thrash = captureTrace(() -> incident().gc(78, 97));
        assertTrue(thrash.contains("GC_READ"), "expected the GC event, got:\n" + thrash);
        assertTrue(thrash.contains("\"thrashing\":true"), "78% pauses with a 97% heap is thrash, got:\n" + thrash);

        String healthy = captureTrace(() -> incident().gc(2, 41));
        assertTrue(healthy.contains("\"thrashing\":false"), "a breathing heap is not thrash, got:\n" + healthy);
    }

    @Test
    void theDeadlockSectionIsTheOneDiagnosisTheJvmProvesItself() {
        String out = captureTrace(() -> incident().deadlock("http-nio-8080-exec-12", "scheduler-1",
                "0x00000007c0a11e40 and 0x00000007c0a11e58"));
        assertTrue(out.contains("DEADLOCK_FOUND"), "expected the deadlock event, got:\n" + out);
        assertTrue(out.contains("\"deadlock\""), "the deadlock must be in the state, got:\n" + out);
        assertTrue(out.contains("Found one Java-level deadlock"), "quote what the JVM prints, got:\n" + out);
    }

    @Test
    void littlesLawExplainsHowFastAPoolDisappears() {
        String out = captureTrace(() -> incident().capacity(200, 8000, 120));
        assertTrue(out.contains("SATURATION_COMPUTED"), "expected the arithmetic event, got:\n" + out);
        assertTrue(out.contains("\"capacityPerSecond\":25"), "200 workers at 8s each is 25 req/s, got:\n" + out);
        assertTrue(out.contains("\"overloaded\":true"), "120 req/s against 25 req/s is overload, got:\n" + out);
        assertTrue(out.contains("\"exhaustMillis\":1666"), "the pool must be gone in 1666ms, got:\n" + out);
    }

    @Test
    void aFixWithNoConfirmedCauseIsReportedAsAGuess() {
        String out = captureTrace(() -> {
            VisualHungService incident = incident();
            incident.fix("double the worker pool to 400");
            incident.verify("curl --max-time 5 /api/payments/42", false);
        });
        assertTrue(out.contains("BLIND_FIX"), "expected the blind-fix event, got:\n" + out);
        assertTrue(out.contains("STILL_DOWN"), "expected the failed verification event, got:\n" + out);
        assertTrue(out.contains("fixed-without-a-cause"), "it must be recorded as a misstep, got:\n" + out);
        assertTrue(out.contains("\"blind\":true"), "the fix itself must be marked blind, got:\n" + out);
        assertTrue(out.contains("\"stage\":\"still-down\""), "a failed verification reopens it, got:\n" + out);
    }

    @Test
    void aConfirmedCauseTurnsTheSameChangeIntoEngineering() {
        String out = captureTrace(() -> {
            VisualHungService incident = incident();
            incident.confirm("no read timeout on the payments provider client",
                    "198 of 200 workers parked in socketRead0 on the same host in all three dumps");
            incident.fix("a 2s read timeout plus a separate 20-thread pool for the provider client");
            incident.verify("curl --max-time 5 /api/payments/42", true);
            incident.guard("alert on successful requests per second, not on process liveness");
        });
        assertTrue(out.contains("ROOT_CAUSE_CONFIRMED"), "expected the root-cause event, got:\n" + out);
        assertTrue(out.contains("FIX_APPLIED"), "expected the fix event, got:\n" + out);
        assertTrue(out.contains("RECOVERY_VERIFIED"), "expected the verification event, got:\n" + out);
        assertTrue(out.contains("GUARD_ADDED"), "expected the follow-up event, got:\n" + out);
        assertTrue(out.contains("\"blind\":false"), "the fix must not be blind, got:\n" + out);
        assertTrue(out.contains("\"stage\":\"recovered\""), "a passing check closes it, got:\n" + out);
    }

    @Test
    void diagnosingWithoutRestoringServiceIsRecordedAsLeavingUsersDown() {
        String down = captureTrace(() -> incident().confirm("a deadlock between the scheduler and the request path",
                "the JVM printed the cycle in all three dumps"));
        assertTrue(down.contains("left-users-down"), "no mitigation before the root cause must be flagged, got:\n" + down);

        String restored = captureTrace(() -> {
            VisualHungService incident = incident();
            incident.restore("roll back to the previous release", "traffic recovers in 90 seconds");
            incident.confirm("a deadlock between the scheduler and the request path",
                    "the JVM printed the cycle in all three dumps");
        });
        assertFalse(restored.contains("left-users-down"), "mitigating first must clear the flag, got:\n" + restored);
    }

    @Test
    void theReviewReadsTheWorksheetBackWithTheClockAndTheMissteps() {
        String out = captureTrace(() -> {
            VisualHungService incident = incident();
            incident.restartFirst("restart every pod");
            incident.review();
        });
        assertTrue(out.contains("INCIDENT_REVIEW"), "expected the review event, got:\n" + out);
        assertTrue(out.contains("T+2m"), "the review must charge the elapsed minutes, got:\n" + out);
        assertTrue(out.contains("missteps: restarted-blind"), "the review must list the missteps, got:\n" + out);
    }
}
