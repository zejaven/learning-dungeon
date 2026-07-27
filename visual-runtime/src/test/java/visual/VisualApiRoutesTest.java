package visual;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualApiRoutesTest {

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

    private static VisualApiRoutes employees() {
        VisualApiRoutes api = VisualApiRoutes.forService("Employee API");
        api.expose("GET", "/employees");
        api.expose("POST", "/employees");
        api.expose("GET", "/employees/{id}");
        api.expose("PUT", "/employees/{id}");
        api.expose("DELETE", "/employees/{id}");
        return api;
    }

    @Test
    void aFreshSurfaceAnnouncesItself() {
        String out = captureTrace(() -> VisualApiRoutes.forService("Employee API"));
        assertTrue(out.contains("API_READY"), "expected the opening event, got:\n" + out);
    }

    @Test
    void nounPathsCarryNoFindings() {
        String out = captureTrace(() -> {
            employees().review();
        });
        assertTrue(out.contains("ENDPOINT_ADDED"), "expected registration events, got:\n" + out);
        assertFalse(out.contains("NAMING_SMELL"), "clean paths must not be flagged, got:\n" + out);
        assertTrue(out.contains("5 endpoint(s) over 2 URL(s)"),
                "five methods must share two URLs, got:\n" + out);
    }

    @Test
    void aVerbInThePathIsFlagged() {
        String out = captureTrace(() -> {
            VisualApiRoutes api = VisualApiRoutes.forService("Employee API");
            api.expose("GET", "/getEmployeeById");
        });
        assertTrue(out.contains("NAMING_SMELL"), "expected the naming event, got:\n" + out);
        assertTrue(out.contains("verb"), "the reason must mention the verb, got:\n" + out);
    }

    @Test
    void aSingularCollectionAndCamelCaseAreFlagged() {
        String out = captureTrace(() -> {
            VisualApiRoutes api = VisualApiRoutes.forService("Employee API");
            api.expose("GET", "/employee/{id}");
            api.expose("GET", "/employeeDocuments");
        });
        assertTrue(out.contains("singular"), "a singular collection must be reported, got:\n" + out);
        assertTrue(out.contains("mixes cases"), "camelCase must be reported, got:\n" + out);
    }

    @Test
    void ordinaryPluralNounsAreNotMistakenForVerbs() {
        String out = captureTrace(() -> {
            VisualApiRoutes api = VisualApiRoutes.forService("Employee API");
            api.expose("GET", "/addresses");
            api.expose("GET", "/settings");
            api.expose("POST", "/orders/{id}/cancellations");
        });
        assertFalse(out.contains("NAMING_SMELL"),
                "nouns that merely start like a verb must pass, got:\n" + out);
    }

    @Test
    void aGetThatChangesDataIsReportedAsUnsafe() {
        String out = captureTrace(() -> {
            VisualApiRoutes api = VisualApiRoutes.forService("Employee API");
            api.expose("GET", "/employees/42/delete");
        });
        assertTrue(out.contains("UNSAFE_GET"), "expected the safety event, got:\n" + out);
        assertFalse(out.contains("READ_BEHIND_POST"), "this is the opposite mistake, got:\n" + out);
    }

    @Test
    void aReadBehindPostIsReported() {
        String out = captureTrace(() -> {
            VisualApiRoutes api = VisualApiRoutes.forService("Employee API");
            api.expose("POST", "/getEmployees");
        });
        assertTrue(out.contains("READ_BEHIND_POST"), "expected the method-mismatch event, got:\n" + out);
        assertFalse(out.contains("UNSAFE_GET"), "this is the opposite mistake, got:\n" + out);
    }

    @Test
    void routingPicksThePathThenTheMethod() {
        String out = captureTrace(() -> {
            VisualApiRoutes api = employees();
            api.request("GET", "/employees/42");
            api.request("DELETE", "/employees/42");
        });
        assertTrue(out.contains("REQUEST_ROUTED"), "expected a routed request, got:\n" + out);
        assertTrue(out.contains("200 OK"), "a GET answers 200, got:\n" + out);
        assertTrue(out.contains("204 No Content"), "a DELETE answers 204, got:\n" + out);
    }

    @Test
    void aKnownUrlWithAnUnknownMethodIs405() {
        String out = captureTrace(() -> {
            VisualApiRoutes api = employees();
            api.request("PATCH", "/employees/42");
        });
        assertTrue(out.contains("METHOD_NOT_ALLOWED"), "expected a 405, got:\n" + out);
        assertTrue(out.contains("Allow: GET, PUT, DELETE"),
                "the allowed methods must be listed, got:\n" + out);
    }

    @Test
    void aSingularUrlIs404AndSuggestsThePluralOne() {
        String out = captureTrace(() -> {
            VisualApiRoutes api = employees();
            api.request("GET", "/employee/42");
        });
        assertTrue(out.contains("NO_ROUTE"), "expected a 404, got:\n" + out);
        assertTrue(out.contains("/employees/{id} is registered"),
                "the near miss must be suggested, got:\n" + out);
    }

    @Test
    void queryParametersSelectPartOfTheSameCollection() {
        String out = captureTrace(() -> {
            VisualApiRoutes api = employees();
            api.request("GET", "/employees?department=finance&sort=-hiredAt&page=2");
        });
        assertTrue(out.contains("QUERY_FILTERED"), "expected the query event, got:\n" + out);
        assertTrue(out.contains("department=finance"), "the parameters must be visible, got:\n" + out);
        assertTrue(out.contains("3 query parameter(s)"), "all three must be parsed, got:\n" + out);
    }

    @Test
    void renamingMovesTheVerbIntoTheMethod() {
        String out = captureTrace(() -> {
            VisualApiRoutes api = VisualApiRoutes.forService("Employee API");
            api.expose("GET", "/getEmployeeById");
            api.expose("POST", "/deleteEmployee");
            api.rename("GET", "/getEmployeeById", "GET", "/employees/{id}");
            api.rename("POST", "/deleteEmployee", "DELETE", "/employees/{id}");
            api.request("GET", "/employees/42");
            api.review();
        });
        assertTrue(out.contains("ENDPOINT_RENAMED"), "expected the rename event, got:\n" + out);
        assertTrue(out.contains("2 endpoint(s) over 1 URL(s)"),
                "both actions must collapse onto one URL, got:\n" + out);
        assertTrue(out.contains("0 naming finding(s)"),
                "the renamed surface must be clean, got:\n" + out);
    }

    @Test
    void nestingDeeperThanTwoIdsIsFlagged() {
        String out = captureTrace(() -> {
            VisualApiRoutes api = VisualApiRoutes.forService("Employee API");
            api.expose("GET", "/departments/{deptId}/employees/{id}/documents/{docId}");
        });
        assertTrue(out.contains("NAMING_SMELL"), "deep nesting must be flagged, got:\n" + out);
        assertTrue(out.contains("nests 3 ids deep"), "the depth must be reported, got:\n" + out);
    }

    @Test
    void everyTraceLineIsPrefixed() {
        String out = captureTrace(() -> {
            VisualApiRoutes api = employees();
            api.expose("GET", "/employees.json");
            api.expose("GET", "/employees/byDepartment/{name}");
            api.expose("GET", "/employees/{id}/salary");
            api.request("GET", "/employees?page=1");
            api.request("HEAD", "/employees/42");
            api.request("GET", "/nope");
            api.rename("GET", "/employees.json", "GET", "/employees");
            api.review();
        });
        out.lines().forEach(line -> {
            if (!line.isEmpty()) {
                assertTrue(line.startsWith(Trace.PREFIX), "unexpected non-trace line: " + line);
            }
        });
    }
}
