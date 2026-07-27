package visual;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualErrorContractTest {

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

    private static VisualErrorContract employees() {
        VisualErrorContract api = VisualErrorContract.forApi("employee-api", "employee");
        api.define("employee.not_found", "EmployeeNotFoundException", 404, false);
        api.define("employee.email_taken", "EmailAlreadyUsedException", 409, false);
        api.define("employee.validation_failed", "MethodArgumentNotValidException", 400, false);
        api.define("employee.payroll_unavailable", "PayrollTimeoutException", 503, true);
        return api;
    }

    @Test
    void aFreshApiAnnouncesItself() {
        String out = captureTrace(() -> VisualErrorContract.forApi("employee-api", "employee"));
        assertTrue(out.contains("CONTRACT_READY"), "expected the opening event, got:\n" + out);
    }

    @Test
    void aWellFormedCatalogHasNoFindings() {
        String out = captureTrace(() -> employees().review());
        assertTrue(out.contains("CODE_DEFINED"), "expected declaration events, got:\n" + out);
        assertTrue(out.contains("4 code(s) over 4 status(es), 0 finding(s)"),
                "the clean catalog must pass, got:\n" + out);
        assertFalse(out.contains("CODE_UNSTABLE"), "these codes are well shaped, got:\n" + out);
        assertFalse(out.contains("RETRY_ADVICE_WRONG"), "the retry advice matches, got:\n" + out);
    }

    @Test
    void aCodeThatIsReallyASentenceIsFlagged() {
        String out = captureTrace(() -> {
            VisualErrorContract api = VisualErrorContract.forApi("employee-api", "employee");
            api.define("Employee not found", "EmployeeNotFoundException", 404, false);
        });
        assertTrue(out.contains("CODE_UNSTABLE"), "expected the code-shape event, got:\n" + out);
        assertTrue(out.contains("is a sentence, not an identifier"), "the reason must be named, got:\n" + out);
    }

    @Test
    void aCodeWithoutANamespaceIsFlagged() {
        String out = captureTrace(() -> {
            VisualErrorContract api = VisualErrorContract.forApi("employee-api", "employee");
            api.define("not_found", "EmployeeNotFoundException", 404, false);
        });
        assertTrue(out.contains("has no namespace"), "the missing prefix must be named, got:\n" + out);
        assertTrue(out.contains("employee.not_found"), "the fixed code must be suggested, got:\n" + out);
    }

    @Test
    void anErrorCarriedByASuccessStatusIsFlagged() {
        String out = captureTrace(() -> {
            VisualErrorContract api = VisualErrorContract.forApi("employee-api", "employee");
            api.define("employee.validation_failed", "MethodArgumentNotValidException", 200, false);
        });
        assertTrue(out.contains("STATUS_MISUSED"), "expected the status event, got:\n" + out);
        assertTrue(out.contains("200 OK"), "the offending status must be named, got:\n" + out);
    }

    @Test
    void retryAdviceMustMatchTheStatusInBothDirections() {
        String out = captureTrace(() -> {
            VisualErrorContract api = VisualErrorContract.forApi("employee-api", "employee");
            api.define("employee.email_taken", "EmailAlreadyUsedException", 409, true);
            api.define("employee.payroll_unavailable", "PayrollTimeoutException", 503, false);
            api.review();
        });
        long flagged = out.lines().filter(line -> line.contains("RETRY_ADVICE_WRONG")).count();
        assertEquals(2, flagged, "both directions must be flagged, got:\n" + out);
        assertTrue(out.contains("2 finding(s)"), "the review must count them, got:\n" + out);
    }

    @Test
    void aMappedExceptionBecomesItsCodeStatusBodyAndLogLine() {
        String out = captureTrace(() -> {
            VisualErrorContract api = employees();
            api.callFails("GET", "/employees/9999", "EmployeeNotFoundException");
        });
        assertTrue(out.contains("EXCEPTION_THROWN"), "expected the throw, got:\n" + out);
        assertTrue(out.contains("EXCEPTION_MAPPED"), "expected the mapping, got:\n" + out);
        assertTrue(out.contains("404 Not Found"), "the mapped status must be used, got:\n" + out);
        assertTrue(out.contains("ERROR_RESPONSE"), "expected the body, got:\n" + out);
        assertTrue(out.contains("ERROR_LOGGED"), "expected the log line, got:\n" + out);
        assertFalse(out.contains("UNMAPPED_EXCEPTION"), "this one is mapped, got:\n" + out);
    }

    @Test
    void aClientErrorIsLoggedQuietlyAndAServerErrorLoudly() {
        String out = captureTrace(() -> {
            VisualErrorContract api = employees();
            api.callFails("GET", "/employees/9999", "EmployeeNotFoundException");
            api.callFails("POST", "/employees/42/payroll", "NullPointerException");
        });
        assertTrue(out.contains("\"level\":\"DEBUG\""), "a 4xx must stay at DEBUG, got:\n" + out);
        assertTrue(out.contains("\"level\":\"ERROR\""), "a 5xx must be logged at ERROR, got:\n" + out);
        assertTrue(out.contains("+ stack trace"), "a 5xx log line carries the stack, got:\n" + out);
    }

    @Test
    void anUnmappedExceptionFallsIntoTheCatchAll() {
        String out = captureTrace(() -> {
            VisualErrorContract api = employees();
            api.callFails("POST", "/employees/42/payroll", "PayrollLedgerException");
            api.review();
        });
        assertTrue(out.contains("UNMAPPED_EXCEPTION"), "expected the catch-all event, got:\n" + out);
        assertTrue(out.contains("500 Internal Server Error"), "it must become a 500, got:\n" + out);
        assertTrue(out.contains("employee.internal_error"), "the generic code must be used, got:\n" + out);
        assertTrue(out.contains("1 exception(s) still unmapped"), "the review must report it, got:\n" + out);
    }

    @Test
    void mappingAnExceptionAfterwardsChangesTheAnswer() {
        String out = captureTrace(() -> {
            VisualErrorContract api = VisualErrorContract.forApi("employee-api", "employee");
            api.callFails("POST", "/employees/42/payroll", "PayrollTimeoutException");
            api.define("employee.payroll_unavailable", "PayrollTimeoutException", 503, true);
            api.callFails("POST", "/employees/42/payroll", "PayrollTimeoutException");
        });
        assertTrue(out.contains("500 Internal Server Error"), "unmapped is a 500 first, got:\n" + out);
        assertTrue(out.contains("503 Service Unavailable"), "mapping it changes the status, got:\n" + out);
        assertTrue(out.contains("\"name\":\"retryable\",\"value\":\"true\""),
                "the answer must advertise the retry, got:\n" + out);
    }

    @Test
    void validationNamesEveryOffendingField() {
        String out = captureTrace(() -> {
            VisualErrorContract api = employees();
            api.callInvalid("POST", "/employees", "email: must not be blank", "salary: must be positive");
        });
        assertTrue(out.contains("VALIDATION_REJECTED"), "expected the rejection, got:\n" + out);
        assertTrue(out.contains("400 Bad Request"), "a broken body is a 400, got:\n" + out);
        assertTrue(out.contains("errors[0]"), "the field list must be structured, got:\n" + out);
        assertTrue(out.contains("errors[1]"), "every field must be reported, got:\n" + out);
    }

    @Test
    void theSameTraceIdIsInTheBodyAndInTheLog() {
        String out = captureTrace(() -> {
            VisualErrorContract api = employees();
            api.callFails("GET", "/employees/9999", "EmployeeNotFoundException");
        });
        String marker = "traceId=";
        int at = out.indexOf(marker);
        assertTrue(at > 0, "the log line must carry a traceId, got:\n" + out);
        String fromLog = out.substring(at + marker.length(), at + marker.length() + 12);
        assertTrue(out.contains("\"traceId\",\"value\":\"" + fromLog + "\""),
                "the body must carry the same traceId (" + fromLog + "), got:\n" + out);
    }

    @Test
    void aRawExceptionInTheBodyIsFlaggedAsALeak() {
        String out = captureTrace(() -> {
            VisualErrorContract api = employees();
            api.leakInternals("POST", "/employees", "DataIntegrityViolationException",
                    "ERROR: duplicate key value violates unique constraint");
            api.review();
        });
        assertTrue(out.contains("INTERNALS_LEAKED"), "expected the leak event, got:\n" + out);
        assertTrue(out.contains("\"leaked\":true"), "the leaking fields must be marked, got:\n" + out);
        assertFalse(out.contains("ERROR_RESPONSE"), "the contract body is not what was sent, got:\n" + out);
    }

    @Test
    void anErrorCaughtInTheControllerBecomesASuccess() {
        String out = captureTrace(() -> {
            VisualErrorContract api = employees();
            api.swallow("GET", "/employees/9999", "EmployeeNotFoundException", "no employee 9999");
        });
        assertTrue(out.contains("ERROR_SWALLOWED"), "expected the swallow event, got:\n" + out);
        assertTrue(out.contains("200 OK"), "the failure is answered with a success, got:\n" + out);
        assertFalse(out.contains("ERROR_LOGGED"), "nothing is logged either, got:\n" + out);
    }

    @Test
    void aSuccessfulRequestNeedsNoneOfIt() {
        String out = captureTrace(() -> employees().call("GET", "/employees/42"));
        assertTrue(out.contains("REQUEST_OK"), "expected the happy path event, got:\n" + out);
        assertFalse(out.contains("EXCEPTION_THROWN"), "nothing throws here, got:\n" + out);
    }

    @Test
    void everyTraceLineIsPrefixed() {
        String out = captureTrace(() -> {
            VisualErrorContract api = employees();
            api.define("Employee not found", "LegacyMissingException", 200, true);
            api.call("GET", "/employees/42");
            api.callFails("GET", "/employees/9999", "EmployeeNotFoundException");
            api.callFails("POST", "/employees/42/payroll", "PayrollLedgerException");
            api.callInvalid("POST", "/employees", "email: must not be blank");
            api.leakInternals("POST", "/employees", "DataIntegrityViolationException", "ERROR: duplicate key");
            api.swallow("GET", "/employees/9999", "EmployeeNotFoundException", "no employee 9999");
            api.review();
        });
        out.lines().forEach(line -> {
            if (!line.isEmpty()) {
                assertTrue(line.startsWith(Trace.PREFIX), "unexpected non-trace line: " + line);
            }
        });
    }
}
