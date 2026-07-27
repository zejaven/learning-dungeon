package visual;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualIncidentTriageTest {

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

    private static VisualIncidentTriage incident() {
        return VisualIncidentTriage.reported("employee-api", "POST /employees", "nothing works");
    }

    @Test
    void aReportIsAnnouncedAsAClaim() {
        String out = captureTrace(VisualIncidentTriageTest::incident);
        assertTrue(out.contains("INCIDENT_REPORTED"), "expected the opening event, got:\n" + out);
        assertTrue(out.contains("\"stage\":\"reported\""), "the incident starts unconfirmed, got:\n" + out);
        assertTrue(out.contains("\"severity\":\"unknown\""), "severity is not known yet, got:\n" + out);
    }

    @Test
    void clarifyingTurnsTheClaimIntoFactsAndLeavesOpenQuestionsVisible() {
        String out = captureTrace(() -> {
            VisualIncidentTriage incident = incident();
            incident.clarify("which request?", "POST /employees");
            incident.unanswered("since when exactly?");
        });
        assertTrue(out.contains("CLAIM_CLARIFIED"), "expected the answered question, got:\n" + out);
        assertTrue(out.contains("QUESTION_UNANSWERED"), "expected the open question, got:\n" + out);
        assertTrue(out.contains("\"openQuestions\":1"), "the open question must be counted, got:\n" + out);
    }

    @Test
    void dismissingTheReportIsRecordedAsAMisstepThatCostsTime() {
        String out = captureTrace(() -> {
            VisualIncidentTriage incident = incident();
            incident.dismiss("it works on my machine");
            incident.review();
        });
        assertTrue(out.contains("REPORT_DISMISSED"), "expected the dismissal event, got:\n" + out);
        assertTrue(out.contains("dismissed-report"), "it must be recorded as a misstep, got:\n" + out);
        assertTrue(out.contains("T+30m"), "half an hour must be charged for it, got:\n" + out);
    }

    @Test
    void aFailingReproductionMakesTheIncidentConfirmed() {
        String out = captureTrace(() -> {
            VisualIncidentTriage incident = incident();
            incident.reproduce("POST /employees with a valid body", true, "500 employee.internal_error");
        });
        assertTrue(out.contains("REPRODUCED"), "expected the reproduction event, got:\n" + out);
        assertTrue(out.contains("\"stage\":\"confirmed\""), "it must become confirmed, got:\n" + out);
        assertTrue(out.contains("\"failed\":true"), "the reproduction must be recorded, got:\n" + out);
    }

    @Test
    void aPassingReproductionNarrowsInsteadOfClosing() {
        String out = captureTrace(() -> {
            VisualIncidentTriage incident = incident();
            incident.reproduce("POST /employees from curl", false, "201 Created");
        });
        assertTrue(out.contains("NOT_REPRODUCED"), "expected the narrowing event, got:\n" + out);
        assertTrue(out.contains("\"stage\":\"not-reproduced\""), "the stage must say so, got:\n" + out);
        assertFalse(out.contains("\"event\":\"REPRODUCED\""), "it did not reproduce, got:\n" + out);
    }

    @Test
    void signalsAreReadAndAMissingOneIsItselfAFinding() {
        String out = captureTrace(() -> {
            VisualIncidentTriage incident = incident();
            incident.readSignal("5xx rate", "0.2% -> 31%", true);
            incident.missingSignal("an alert on the 5xx rate");
        });
        assertTrue(out.contains("SIGNAL_READ"), "expected the signal event, got:\n" + out);
        assertTrue(out.contains("\"verdict\":\"anomalous\""), "the spike must be marked, got:\n" + out);
        assertTrue(out.contains("SIGNAL_MISSING"), "expected the blind-spot event, got:\n" + out);
        assertTrue(out.contains("flying-blind"), "the blind spot must be a misstep, got:\n" + out);
    }

    @Test
    void severityFallsOutOfTheMeasuredBlastRadius() {
        String wide = captureTrace(() ->
                incident().scope("every write to /employees", 78, "the HR web client"));
        assertTrue(wide.contains("BLAST_RADIUS_SET"), "expected the scoping event, got:\n" + wide);
        assertTrue(wide.contains("\"severity\":\"critical\""), "78% must be critical, got:\n" + wide);

        String narrow = captureTrace(() ->
                incident().scope("one tenant's imports", 2, "the bulk importer"));
        assertTrue(narrow.contains("\"severity\":\"low\""), "2% must be low, got:\n" + narrow);
    }

    @Test
    void mitigationRestoresServiceWithoutClosingTheIncident() {
        String out = captureTrace(() -> {
            VisualIncidentTriage incident = incident();
            incident.scope("every write to /employees", 78, "the HR web client");
            incident.mitigate("roll back to the previous release", "5xx rate back to 0.2%");
        });
        assertTrue(out.contains("MITIGATED"), "expected the mitigation event, got:\n" + out);
        assertTrue(out.contains("\"stage\":\"mitigated\""), "the stage must say mitigated, got:\n" + out);
        assertFalse(out.contains("ROOT_CAUSE_CONFIRMED"), "nothing is diagnosed yet, got:\n" + out);
    }

    @Test
    void diagnosingACriticalIncidentBeforeMitigatingIsRecorded() {
        String out = captureTrace(() -> {
            VisualIncidentTriage incident = incident();
            incident.scope("every write to /employees", 78, "the HR web client");
            incident.suspect("yesterday's deploy", "the timing lines up");
            incident.review();
        });
        assertTrue(out.contains("diagnosed-while-down"), "the ordering must be flagged, got:\n" + out);
    }

    @Test
    void aSuspectAlwaysLeavesWithEvidenceAttached() {
        String out = captureTrace(() -> {
            VisualIncidentTriage incident = incident();
            incident.suspect("yesterday's deploy", "the timing lines up");
            incident.ruleOut("yesterday's deploy", "the previous release fails identically");
            incident.confirm("the nightly import wrote 40k rows with a null email",
                    "the first failure is 20 minutes after the job finished");
        });
        assertTrue(out.contains("SUSPECT_RAISED"), "expected the hypothesis event, got:\n" + out);
        assertTrue(out.contains("SUSPECT_RULED_OUT"), "expected the elimination event, got:\n" + out);
        assertTrue(out.contains("\"status\":\"ruled-out\""), "the suspect must be closed, got:\n" + out);
        assertTrue(out.contains("ROOT_CAUSE_CONFIRMED"), "expected the confirmation event, got:\n" + out);
        assertTrue(out.contains("\"status\":\"confirmed\""), "the cause must be marked, got:\n" + out);
    }

    @Test
    void rulingOutAnUnraisedSuspectStillRecordsIt() {
        String out = captureTrace(() -> {
            VisualIncidentTriage incident = incident();
            incident.ruleOut("a config change", "the config map is byte-identical to last week's");
        });
        assertTrue(out.contains("\"name\":\"a config change\""), "the suspect must exist, got:\n" + out);
        assertTrue(out.contains("\"status\":\"ruled-out\""), "and be closed, got:\n" + out);
    }

    @Test
    void fixingWithoutAConfirmedCauseIsABlindFix() {
        String out = captureTrace(() -> {
            VisualIncidentTriage incident = incident();
            incident.fix("add a null check and redeploy");
            incident.review();
        });
        assertTrue(out.contains("BLIND_FIX"), "expected the blind-fix event, got:\n" + out);
        assertTrue(out.contains("blind-fix"), "it must be recorded as a misstep, got:\n" + out);
        assertFalse(out.contains("FIX_APPLIED"), "an unconfirmed change is not a fix, got:\n" + out);
    }

    @Test
    void skippingPhasesIsVisibleInTheTrack() {
        String out = captureTrace(() -> {
            VisualIncidentTriage incident = incident();
            incident.fix("add a null check and redeploy");
            incident.review();
        });
        assertTrue(out.contains("\"name\":\"reproduce\",\"state\":\"skipped\""),
                "jumping to the fix must skip reproduce, got:\n" + out);
        assertTrue(out.contains("skipped: clarify, reproduce, measure, mitigate, diagnose"),
                "the review must list them, got:\n" + out);
    }

    @Test
    void aFixOnAConfirmedCauseIsAppliedAndVerifiedAgainstTheFailingRequest() {
        String out = captureTrace(() -> {
            VisualIncidentTriage incident = incident();
            incident.reproduce("POST /employees with a valid body", true, "500 employee.internal_error");
            incident.confirm("a null email in imported rows", "every failing traceId hits the same row");
            incident.fix("treat a missing email as absent instead of dereferencing it");
            incident.verify("POST /employees with a valid body", true);
        });
        assertTrue(out.contains("FIX_APPLIED"), "expected the fix event, got:\n" + out);
        assertTrue(out.contains("FIX_VERIFIED"), "expected the verification event, got:\n" + out);
        assertTrue(out.contains("\"stage\":\"closed\""), "a verified fix closes it, got:\n" + out);
        assertFalse(out.contains("BLIND_FIX"), "the cause was confirmed, got:\n" + out);
    }

    @Test
    void aFixThatDoesNotFixItReopensTheIncident() {
        String out = captureTrace(() -> {
            VisualIncidentTriage incident = incident();
            incident.reproduce("POST /employees with a valid body", true, "500 employee.internal_error");
            incident.confirm("a null email in imported rows", "every failing traceId hits the same row");
            incident.fix("treat a missing email as absent instead of dereferencing it");
            incident.verify("POST /employees with a valid body", false);
        });
        assertTrue(out.contains("FIX_UNVERIFIED"), "expected the failed verification, got:\n" + out);
        assertTrue(out.contains("\"stage\":\"reopened\""), "it must reopen, got:\n" + out);
        assertTrue(out.contains("fix-did-not-fix-it"), "and be recorded, got:\n" + out);
    }

    @Test
    void verifyingWithoutAFailingReproductionIsFlagged() {
        String out = captureTrace(() -> {
            VisualIncidentTriage incident = incident();
            incident.confirm("a null email in imported rows", "every failing traceId hits the same row");
            incident.fix("treat a missing email as absent");
            incident.verify("the unit test suite", true);
        });
        assertTrue(out.contains("nothing-to-verify-against"),
                "verifying with no reproduction must be flagged, got:\n" + out);
    }

    @Test
    void guardsAreTheOnlyPermanentOutput() {
        String out = captureTrace(() -> {
            VisualIncidentTriage incident = incident();
            incident.guard("alert on the 5xx rate at deploy + 5 minutes");
            incident.guard("a test for an imported row with no email");
        });
        long added = out.lines().filter(line -> line.contains("GUARD_ADDED")).count();
        assertEquals(2, added, "both follow-ups must be recorded, got:\n" + out);
    }

    @Test
    void theTestGapIsNamedSeparatelyFromTheRootCause() {
        String out = captureTrace(() -> {
            VisualIncidentTriage incident = incident();
            incident.confirm("a null email in imported rows", "every failing traceId hits the same row");
            incident.testGap("every fixture row had an email, because the fixtures were written by hand");
            incident.review();
        });
        assertTrue(out.contains("TEST_GAP_NAMED"), "expected the test-gap event, got:\n" + out);
        assertTrue(out.contains("\"testGap\":\"every fixture row had an email"),
                "the gap must be in the state, got:\n" + out);
    }

    @Test
    void theReviewReportsTheClockTheOpenQuestionsAndTheMissteps() {
        String out = captureTrace(() -> {
            VisualIncidentTriage incident = incident();
            incident.unanswered("since when exactly?");
            incident.reproduce("POST /employees with a valid body", true, "500 employee.internal_error");
            incident.fix("add a null check and redeploy");
            incident.review();
        });
        assertTrue(out.contains("TRIAGE_REVIEW"), "expected the review event, got:\n" + out);
        assertTrue(out.contains("1 still open"), "the open question must be reported, got:\n" + out);
        assertTrue(out.contains("root cause not found"), "nothing was confirmed, got:\n" + out);
        assertTrue(out.contains("missteps: blind-fix"), "the misstep must be reported, got:\n" + out);
    }

    @Test
    void aCleanTriageEndsWithNoMisstepsAndNoSkippedPhases() {
        String out = captureTrace(() -> {
            VisualIncidentTriage incident = incident();
            incident.clarify("which request?", "POST /employees");
            incident.reproduce("POST /employees with a valid body", true, "500 employee.internal_error");
            incident.readSignal("5xx rate", "0.2% -> 31%", true);
            incident.scope("every write to /employees", 78, "the HR web client");
            incident.mitigate("roll back to the previous release", "5xx rate back to 0.2%");
            incident.confirm("a null email in imported rows", "every failing traceId hits the same row");
            incident.testGap("every fixture row had an email");
            incident.fix("treat a missing email as absent");
            incident.verify("POST /employees with a valid body", true);
            incident.guard("alert on the 5xx rate at deploy + 5 minutes");
            incident.review();
        });
        assertTrue(out.contains("no phase skipped"), "every phase must be reached, got:\n" + out);
        assertTrue(out.contains("no missteps"), "a clean run has none, got:\n" + out);
        assertTrue(out.contains("\"stage\":\"closed\""), "it must end closed, got:\n" + out);
    }

    @Test
    void everyTraceLineIsPrefixed() {
        String out = captureTrace(() -> {
            VisualIncidentTriage incident = incident();
            incident.dismiss("it works on my machine");
            incident.clarify("which request?", "POST /employees");
            incident.unanswered("since when exactly?");
            incident.reproduce("POST /employees with a valid body", true, "500 employee.internal_error");
            incident.readSignal("5xx rate", "0.2% -> 31%", true);
            incident.missingSignal("an alert on the 5xx rate");
            incident.scope("every write to /employees", 78, "the HR web client");
            incident.mitigate("roll back to the previous release", "5xx rate back to 0.2%");
            incident.suspect("yesterday's deploy", "the timing lines up");
            incident.ruleOut("yesterday's deploy", "the previous release fails identically");
            incident.confirm("a null email in imported rows", "every failing traceId hits the same row");
            incident.testGap("every fixture row had an email");
            incident.fix("treat a missing email as absent");
            incident.verify("POST /employees with a valid body", true);
            incident.guard("alert on the 5xx rate at deploy + 5 minutes");
            incident.review();
        });
        out.lines().forEach(line -> {
            if (!line.isEmpty()) {
                assertTrue(line.startsWith(Trace.PREFIX), "unexpected non-trace line: " + line);
            }
        });
    }
}
