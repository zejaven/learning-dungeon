package visual;

import org.junit.jupiter.api.Test;
import visual.VisualClientServer.Request;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualClientServerTest {

    private static final String APP = "shop-web";
    private static final String API = "shop-api";

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
    void openingTheAppReportsAnEmptyFrontend() {
        String out = captureTrace(() -> VisualClientServer.open(APP, API));
        assertTrue(out.contains("APP_LOADED"), "expected the load event, got:\n" + out);
        assertTrue(out.contains(API), "the API must be named, got:\n" + out);
    }

    @Test
    void oneCallWalksTheWholeRoundTrip() {
        String out = captureTrace(() -> {
            VisualClientServer app = VisualClientServer.open(APP, API);
            app.call(Request.get("/api/employees"));
            app.report();
        });
        assertTrue(out.contains("REQUEST_SENT"), "the request must be built, got:\n" + out);
        assertTrue(out.contains("CALL_PENDING"), "the promise must be pending, got:\n" + out);
        assertTrue(out.contains("ROUTE_MATCHED"), "the router must pick a handler, got:\n" + out);
        assertTrue(out.contains("EmployeeController.list()"), "the handler must be named, got:\n" + out);
        assertTrue(out.contains("RESPONSE_SENT"), "a response must come back, got:\n" + out);
        assertTrue(out.contains("UI_UPDATED"), "the screen must be re-rendered, got:\n" + out);
    }

    @Test
    void anUnmappedPathAnswersWithAValid404() {
        String out = captureTrace(() -> {
            VisualClientServer app = VisualClientServer.open(APP, API);
            app.call(Request.get("/api/employee"));
        });
        assertTrue(out.contains("NOT_FOUND"), "an unknown path must 404, got:\n" + out);
        assertTrue(out.contains("RESPONSE_SENT"), "a 404 is still a response, got:\n" + out);
        assertFalse(out.contains("ROUTE_MATCHED"), "nothing was matched, got:\n" + out);
    }

    @Test
    void aWriteWithoutATokenIsRejectedWithoutTouchingTheStore() {
        String out = captureTrace(() -> {
            VisualClientServer app = VisualClientServer.open(APP, API);
            app.call(Request.post("/api/employees").json("name", "Iris Vogel"));
            app.report();
        });
        assertTrue(out.contains("AUTH_REJECTED"), "an anonymous write must be refused, got:\n" + out);
        assertFalse(out.contains("RESOURCE_CREATED"), "nothing may be created, got:\n" + out);
        assertTrue(out.contains("the server stores 2 employee(s)"), "the store must be untouched, got:\n" + out);
    }

    @Test
    void anExpiredTokenIsRejectedToo() {
        String out = captureTrace(() -> {
            VisualClientServer app = VisualClientServer.open(APP, API);
            app.call(Request.post("/api/employees").json("name", "Iris Vogel").bearer("jwt.ada.expired"));
        });
        assertTrue(out.contains("AUTH_REJECTED"), "an expired token must be refused, got:\n" + out);
        assertTrue(out.contains("jwt.ada.expired"), "the offending token must be named, got:\n" + out);
    }

    @Test
    void signingInStoresATokenAndLeavesNoSessionOnTheServer() {
        String out = captureTrace(() -> {
            VisualClientServer app = VisualClientServer.open(APP, API);
            app.call(Request.post("/api/sessions").json("user", "ada").and("password", "secret"));
            app.call(Request.post("/api/employees").json("name", "Iris Vogel").authenticated());
            app.report();
        });
        assertTrue(out.contains("TOKEN_STORED"), "the token must be stored client-side, got:\n" + out);
        assertTrue(out.contains("RESOURCE_CREATED"), "the authenticated write must succeed, got:\n" + out);
        assertTrue(out.contains("and 0 sessions"), "the server keeps no session, got:\n" + out);
    }

    @Test
    void creatingAnEmployeeAnswersWithTheIdTheServerInvented() {
        String out = captureTrace(() -> {
            VisualClientServer app = VisualClientServer.open(APP, API);
            app.call(Request.post("/api/sessions").json("user", "ada").and("password", "secret"));
            app.call(Request.post("/api/employees").json("name", "Iris Vogel").authenticated());
            app.report();
        });
        assertTrue(out.contains("201 Created"), "a create must answer 201, got:\n" + out);
        assertTrue(out.contains("/api/employees/3"), "Location must carry the new id, got:\n" + out);
        assertTrue(out.contains("records created: 1"), "exactly one record, got:\n" + out);
    }

    @Test
    void aMissingRequiredFieldIsAnsweredWithAMachineReadable400() {
        String out = captureTrace(() -> {
            VisualClientServer app = VisualClientServer.open(APP, API);
            app.call(Request.post("/api/sessions").json("user", "ada").and("password", "secret"));
            app.call(Request.post("/api/employees").json("role", "engineer").authenticated());
            app.report();
        });
        assertTrue(out.contains("VALIDATION_FAILED"), "the server must validate again, got:\n" + out);
        assertTrue(out.contains("400 Bad Request"), "expected a 400, got:\n" + out);
        assertTrue(out.contains("records created: 0"), "nothing may be created, got:\n" + out);
    }

    @Test
    void aBrokenNetworkGivesNoStatusCodeAtAll() {
        String out = captureTrace(() -> {
            VisualClientServer app = VisualClientServer.open(APP, API);
            app.networkDown();
            app.call(Request.get("/api/employees"));
            app.report();
        });
        assertTrue(out.contains("NETWORK_CHANGED"), "the outage must be visible, got:\n" + out);
        assertTrue(out.contains("NETWORK_FAILED"), "the call must fail, got:\n" + out);
        assertFalse(out.contains("RESPONSE_SENT"), "there is no response to send, got:\n" + out);
        assertTrue(out.contains("Failed calls: 1"), "the failure must be counted, got:\n" + out);
    }

    @Test
    void aLostAnswerStillChangedTheServer() {
        String out = captureTrace(() -> {
            VisualClientServer app = VisualClientServer.open(APP, API);
            app.call(Request.post("/api/sessions").json("user", "ada").and("password", "secret"));
            app.dropNextResponse();
            app.call(Request.post("/api/employees").json("name", "Iris Vogel").authenticated());
            app.report();
        });
        assertTrue(out.contains("RESOURCE_CREATED"), "the handler still ran, got:\n" + out);
        assertTrue(out.contains("RESPONSE_LOST"), "the answer must be lost, got:\n" + out);
        assertTrue(out.contains("\"failure\":\"response-lost\""),
                "the call must end without an answer, got:\n" + out);
        assertTrue(out.contains("the browser holds 0 item(s)"),
                "the frontend must not show the row it never heard about, got:\n" + out);
        assertTrue(out.contains("records created: 1"), "the write happened anyway, got:\n" + out);
    }

    @Test
    void retryingAWriteAfterASilentFailureCreatesADuplicate() {
        String out = captureTrace(() -> {
            VisualClientServer app = VisualClientServer.open(APP, API);
            app.call(Request.post("/api/sessions").json("user", "ada").and("password", "secret"));
            app.dropNextResponse();
            app.call(Request.post("/api/employees").json("name", "Iris Vogel").authenticated());
            app.call(Request.post("/api/employees").json("name", "Iris Vogel").authenticated());
            app.report();
        });
        assertTrue(out.contains("DUPLICATE_CREATED"), "the retry must duplicate, got:\n" + out);
        assertTrue(out.contains("duplicates created by a retry: 1"), "one duplicate, got:\n" + out);
        assertTrue(out.contains("records created: 2"), "two records exist, got:\n" + out);
    }

    @Test
    void anIdempotencyKeyMakesTheSameRetryHarmless() {
        String out = captureTrace(() -> {
            VisualClientServer app = VisualClientServer.open(APP, API);
            app.call(Request.post("/api/sessions").json("user", "ada").and("password", "secret"));
            app.dropNextResponse();
            app.call(Request.post("/api/employees").json("name", "Iris Vogel")
                    .authenticated().idempotencyKey("hire-77"));
            app.call(Request.post("/api/employees").json("name", "Iris Vogel")
                    .authenticated().idempotencyKey("hire-77"));
            app.report();
        });
        assertTrue(out.contains("IDEMPOTENT_REPLAY"), "the repeat must be recognised, got:\n" + out);
        assertFalse(out.contains("DUPLICATE_CREATED"), "no duplicate may appear, got:\n" + out);
        assertTrue(out.contains("records created: 1"), "exactly one record, got:\n" + out);
    }

    @Test
    void reloadingThePageEmptiesOnlyTheBrowser() {
        String out = captureTrace(() -> {
            VisualClientServer app = VisualClientServer.open(APP, API);
            app.call(Request.get("/api/employees"));
            app.reloadPage();
            app.report();
        });
        assertTrue(out.contains("PAGE_RELOADED"), "expected the reload event, got:\n" + out);
        assertTrue(out.contains("the browser holds 0 item(s)"), "the frontend must be empty, got:\n" + out);
        assertTrue(out.contains("the server stores 2 employee(s)"), "the server is unaffected, got:\n" + out);
    }

    @Test
    void inspectingThePayloadShowsWhereTheTypesWent() {
        String out = captureTrace(() -> {
            VisualClientServer app = VisualClientServer.open(APP, API);
            app.call(Request.get("/api/employees/1"));
            app.inspectPayload();
        });
        assertTrue(out.contains("PAYLOAD_INSPECTED"), "expected the payload event, got:\n" + out);
        assertTrue(out.contains("LocalDate"), "the Java type must be shown, got:\n" + out);
        assertTrue(out.contains("BigDecimal"), "the money type must be shown, got:\n" + out);
    }

    @Test
    void aRenamedFieldBreaksTheDeployedFrontendWithoutAnyError() {
        String out = captureTrace(() -> {
            VisualClientServer app = VisualClientServer.open(APP, API);
            app.call(Request.get("/api/employees/1"));
            app.readField("name");
            app.renameField("name", "fullName");
            app.call(Request.get("/api/employees/1"));
            app.readField("name");
            app.report();
        });
        assertTrue(out.contains("FIELD_READ"), "the field must read fine at first, got:\n" + out);
        assertTrue(out.contains("CONTRACT_CHANGED"), "the rename must be visible, got:\n" + out);
        assertTrue(out.contains("FIELD_MISSING"), "the frontend must lose the field, got:\n" + out);
        assertTrue(out.contains("200 OK"), "the call itself still succeeds, got:\n" + out);
    }

    @Test
    void aHandlerThatThrowsStillProducesAnAnswer() {
        String out = captureTrace(() -> {
            VisualClientServer app = VisualClientServer.open(APP, API);
            app.call(Request.get("/api/reports/payroll"));
        });
        assertTrue(out.contains("SERVER_ERROR"), "expected a 500, got:\n" + out);
        assertTrue(out.contains("500 Internal Server Error"), "the status line must be sent, got:\n" + out);
        assertTrue(out.contains("RESPONSE_SENT"), "a 500 is still a response, got:\n" + out);
    }

    @Test
    void theServerCannotSpeakFirstUntilASocketIsOpen() {
        String out = captureTrace(() -> {
            VisualClientServer app = VisualClientServer.open(APP, API);
            app.pushFromServer("order-42 shipped");
            app.pollForNews(2);
            app.openSocket();
            app.pushFromServer("order-42 shipped");
            app.report();
        });
        assertTrue(out.contains("PUSH_IMPOSSIBLE"), "a closed connection cannot be pushed to, got:\n" + out);
        assertTrue(out.contains("POLL_EMPTY"), "polling must be visible, got:\n" + out);
        assertTrue(out.contains("SOCKET_OPENED"), "the upgrade must happen, got:\n" + out);
        assertTrue(out.contains("SERVER_PUSHED"), "the push must succeed, got:\n" + out);
        assertTrue(out.contains("polls that returned nothing: 2"), "two empty polls, got:\n" + out);
    }

    @Test
    void everyTraceLineIsPrefixed() {
        String out = captureTrace(() -> {
            VisualClientServer app = VisualClientServer.open(APP, API);
            app.call(Request.get("/api/employees"));
            app.call(Request.get("/api/employees/2"));
            app.inspectPayload();
            app.call(Request.post("/api/sessions").json("user", "ada").and("password", "secret"));
            app.call(Request.post("/api/employees").json("name", "Iris Vogel").authenticated());
            app.call(Request.delete("/api/employees/1").authenticated());
            app.call(Request.get("/api/reports/payroll"));
            app.networkDown();
            app.call(Request.get("/api/employees"));
            app.networkUp();
            app.reloadPage();
            app.pollForNews(1);
            app.openSocket();
            app.pushFromServer("order-42 shipped");
            app.renameField("name", "fullName");
            app.readField("name");
            app.report();
        });
        out.lines().forEach(line -> {
            if (!line.isEmpty()) {
                assertTrue(line.startsWith(Trace.PREFIX), "unexpected non-trace line: " + line);
            }
        });
    }
}
