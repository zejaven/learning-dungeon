package visual;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualRestControllerTest {

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

    private static VisualRestController employees() {
        VisualRestController api =
                VisualRestController.forResource("EmployeeController", "/employees", "Employee");
        api.handler("GET", "/employees", "-", "List<EmployeeView>", 200);
        api.paging("/employees", 20, 100, "department");
        api.handler("GET", "/employees/{id}", "-", "EmployeeView", 200);
        api.handler("POST", "/employees", "CreateEmployeeRequest", "EmployeeView", 201);
        api.handler("DELETE", "/employees/{id}", "-", "-", 204);
        return api;
    }

    @Test
    void aFreshControllerAnnouncesItself() {
        String out = captureTrace(() ->
                VisualRestController.forResource("EmployeeController", "/employees", "Employee"));
        assertTrue(out.contains("CONTROLLER_READY"), "expected the opening event, got:\n" + out);
    }

    @Test
    void aWellFormedControllerHasNoFindings() {
        String out = captureTrace(() -> employees().review());
        assertTrue(out.contains("HANDLER_ADDED"), "expected declaration events, got:\n" + out);
        assertTrue(out.contains("0 design finding(s)"), "the clean design must pass, got:\n" + out);
        assertFalse(out.contains("STATUS_MISMATCH"), "nothing here mismatches, got:\n" + out);
        assertFalse(out.contains("UNBOUNDED_COLLECTION"), "the collection is paged, got:\n" + out);
    }

    @Test
    void theSignatureIsDerivedFromTheDesign() {
        String out = captureTrace(() -> {
            VisualRestController api =
                    VisualRestController.forResource("EmployeeController", "/employees", "Employee");
            api.handler("POST", "/employees", "CreateEmployeeRequest", "EmployeeView", 201);
            api.validation("POST", "/employees", "name: not blank");
        });
        assertTrue(out.contains("ResponseEntity<EmployeeView> create(@RequestBody CreateEmployeeRequest request)"),
                "a create returns ResponseEntity, got:\n" + out);
        assertTrue(out.contains("@Valid @RequestBody"),
                "declaring validation must add @Valid to the signature, got:\n" + out);
    }

    @Test
    void aCreateThatAnswers200IsFlagged() {
        String out = captureTrace(() -> {
            VisualRestController api =
                    VisualRestController.forResource("EmployeeController", "/employees", "Employee");
            api.handler("POST", "/employees", "CreateEmployeeRequest", "EmployeeView", 200);
        });
        assertTrue(out.contains("STATUS_MISMATCH"), "expected the status event, got:\n" + out);
        assertTrue(out.contains("201 Created"), "the expected code must be named, got:\n" + out);
    }

    @Test
    void a204ThatReturnsABodyIsFlagged() {
        String out = captureTrace(() -> {
            VisualRestController api =
                    VisualRestController.forResource("EmployeeController", "/employees", "Employee");
            api.handler("DELETE", "/employees/{id}", "-", "DeleteReport", 204);
        });
        assertTrue(out.contains("BODY_MISMATCH"), "expected the body event, got:\n" + out);
        assertFalse(out.contains("STATUS_MISMATCH"), "204 is the right code for a delete, got:\n" + out);
    }

    @Test
    void aBodyOnGetAndAWriteWithoutOneAreBothFlagged() {
        String out = captureTrace(() -> {
            VisualRestController api =
                    VisualRestController.forResource("EmployeeController", "/employees", "Employee");
            api.handler("GET", "/employees/{id}", "SearchRequest", "EmployeeView", 200);
            api.handler("PATCH", "/employees/{id}", "-", "EmployeeView", 200);
        });
        assertTrue(out.contains("no defined body semantics"), "a GET body must be flagged, got:\n" + out);
        assertTrue(out.contains("declares no request body"), "a bodiless write must be flagged, got:\n" + out);
    }

    @Test
    void theEntityCrossingTheBoundaryIsFlagged() {
        String out = captureTrace(() -> {
            VisualRestController api =
                    VisualRestController.forResource("EmployeeController", "/employees", "Employee");
            api.handler("POST", "/employees", "Employee", "Employee", 201);
            api.handler("GET", "/employees/{id}", "-", "EmployeeView", 200);
        });
        assertTrue(out.contains("ENTITY_EXPOSED"), "expected the entity event, got:\n" + out);
        assertTrue(out.contains("in and out"), "both directions must be reported, got:\n" + out);
        long flagged = out.lines().filter(line -> line.contains("ENTITY_EXPOSED")).count();
        assertTrue(flagged == 1, "only the entity handler may be flagged, got " + flagged + ":\n" + out);
    }

    @Test
    void anUnpagedCollectionIsFlaggedAtReviewAndFixedByPaging() {
        String out = captureTrace(() -> {
            VisualRestController api =
                    VisualRestController.forResource("EmployeeController", "/employees", "Employee");
            api.handler("GET", "/employees", "-", "List<EmployeeView>", 200);
            api.review();
            api.paging("/employees", 20, 100, "department");
            api.review();
        });
        assertTrue(out.contains("UNBOUNDED_COLLECTION"), "expected the paging event, got:\n" + out);
        assertTrue(out.contains("PAGING_ADDED"), "expected the fix event, got:\n" + out);
        assertTrue(out.contains("1 design finding(s)"), "the first review must flag it, got:\n" + out);
        assertTrue(out.contains("0 design finding(s)"), "the second review must be clean, got:\n" + out);
    }

    @Test
    void aRequestGoesBindValidateDelegateRespond() {
        String out = captureTrace(() -> {
            VisualRestController api = employees();
            api.validation("POST", "/employees", "name: not blank", "salary: positive");
            api.call("POST", "/employees");
        });
        assertTrue(out.contains("REQUEST_BOUND"), "expected binding, got:\n" + out);
        assertTrue(out.contains("VALIDATION_PASSED"), "expected validation, got:\n" + out);
        assertTrue(out.contains("employeeService.create(request)"), "expected delegation, got:\n" + out);
        assertTrue(out.contains("RESPONSE_SENT"), "expected the response, got:\n" + out);
        assertTrue(out.contains("201 Created"), "a create answers 201, got:\n" + out);
    }

    @Test
    void pathAndQueryValuesAreBoundSeparately() {
        String out = captureTrace(() -> {
            VisualRestController api = employees();
            api.call("GET", "/employees?department=finance&page=2");
            api.call("GET", "/employees/42");
        });
        assertTrue(out.contains("department=finance"), "query values must bind, got:\n" + out);
        assertTrue(out.contains("size=20 (default)"), "the default page size must show, got:\n" + out);
        assertTrue(out.contains("employeeService.getById(42)"), "the id must bind from the path, got:\n" + out);
    }

    @Test
    void anInvalidBodyStopsBeforeTheService() {
        String out = captureTrace(() -> {
            VisualRestController api = employees();
            api.validation("POST", "/employees", "salary: positive");
            api.callInvalid("POST", "/employees", "salary", "must be positive");
        });
        assertTrue(out.contains("VALIDATION_FAILED"), "expected the rejection, got:\n" + out);
        assertTrue(out.contains("ERROR_MAPPED"), "expected the error mapping, got:\n" + out);
        assertTrue(out.contains("400 Bad Request"), "a broken body is a 400, got:\n" + out);
        assertFalse(out.contains("SERVICE_CALLED"), "the service must not be reached, got:\n" + out);
    }

    @Test
    void anUnknownIdBecomesA404ThroughTheAdvice() {
        String out = captureTrace(() -> {
            VisualRestController api = employees();
            api.callNotFound("GET", "/employees/9999");
        });
        assertTrue(out.contains("SERVICE_CALLED"), "the service is still called, got:\n" + out);
        assertTrue(out.contains("EmployeeNotFoundException"), "the domain exception must be named, got:\n" + out);
        assertTrue(out.contains("404 Not Found"), "it must map to 404, got:\n" + out);
    }

    @Test
    void logicInTheControllerIsFlaggedUntilItIsDelegated() {
        String out = captureTrace(() -> {
            VisualRestController api =
                    VisualRestController.forResource("EmployeeController", "/employees", "Employee");
            api.handler("PUT", "/employees/{id}/salary", "SalaryChangeRequest", "SalaryView", 200);
            api.businessLogic("PUT", "/employees/{id}/salary", "the grade cap");
            api.review();
            api.delegate("PUT", "/employees/{id}/salary");
            api.review();
        });
        assertTrue(out.contains("LOGIC_IN_CONTROLLER"), "expected the fat-controller event, got:\n" + out);
        assertTrue(out.contains("LOGIC_DELEGATED"), "expected the fix event, got:\n" + out);
        assertTrue(out.contains("1 design finding(s)"), "the first review must flag it, got:\n" + out);
        assertTrue(out.contains("0 design finding(s)"), "the second review must be clean, got:\n" + out);
    }

    @Test
    void aSubResourceHandlerNamesItsOwnOperation() {
        String out = captureTrace(() -> {
            VisualRestController api =
                    VisualRestController.forResource("EmployeeController", "/employees", "Employee");
            api.handler("PUT", "/employees/{id}/salary", "SalaryChangeRequest", "SalaryView", 200);
            api.call("PUT", "/employees/42/salary");
        });
        assertTrue(out.contains("employeeService.setSalary(42, request)"),
                "the sub-resource operation must be derived, got:\n" + out);
    }

    @Test
    void anUndeclaredRequestNeverReachesCode() {
        String out = captureTrace(() -> {
            VisualRestController api = employees();
            api.call("PATCH", "/employees/42");
        });
        assertTrue(out.contains("NO_HANDLER"), "expected the missing-handler event, got:\n" + out);
        assertFalse(out.contains("REQUEST_BOUND"), "nothing can bind, got:\n" + out);
    }

    @Test
    void everyTraceLineIsPrefixed() {
        String out = captureTrace(() -> {
            VisualRestController api = employees();
            api.validation("POST", "/employees", "name: not blank");
            api.businessLogic("POST", "/employees", "the grade cap");
            api.delegate("POST", "/employees");
            api.handler("PUT", "/employees/{id}", "UpdateEmployeeRequest", "Employee", 204);
            api.call("GET", "/employees/42");
            api.callInvalid("POST", "/employees", "name", "must not be blank");
            api.callNotFound("GET", "/employees/9999");
            api.call("GET", "/nope");
            api.paging("/nope", 20, 100);
            api.review();
        });
        out.lines().forEach(line -> {
            if (!line.isEmpty()) {
                assertTrue(line.startsWith(Trace.PREFIX), "unexpected non-trace line: " + line);
            }
        });
    }
}
