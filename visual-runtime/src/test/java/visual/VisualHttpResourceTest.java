package visual;

import org.junit.jupiter.api.Test;
import visual.VisualHttpResource.Body;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualHttpResourceTest {

    private static Body user() {
        return Body.of("name", "Ada")
                .and("email", "ada@example.com")
                .and("role", "reader")
                .and("phone", "+1-555-0100");
    }

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
    void servingAResourceEmitsReady() {
        String out = captureTrace(() -> VisualHttpResource.serving("/users/7", user()));
        assertTrue(out.contains("RESOURCE_READY"), "expected a creation event, got:\n" + out);
        assertTrue(out.contains("ada@example.com"), "the stored fields must be visible, got:\n" + out);
    }

    @Test
    void putWithAFullBodyReplacesTheRepresentation() {
        String out = captureTrace(() -> {
            VisualHttpResource api = VisualHttpResource.serving("/users/7", user());
            api.put(Body.of("name", "Ada Lovelace")
                    .and("email", "ada@example.com")
                    .and("role", "admin")
                    .and("phone", "+1-555-0100"));
            api.report();
        });
        assertTrue(out.contains("PUT_REPLACED"), "expected the replacement event, got:\n" + out);
        assertFalse(out.contains("FIELD_LOST"), "a complete body may not lose a field, got:\n" + out);
        assertTrue(out.contains("holds 4 field(s)"), "all four fields must survive, got:\n" + out);
    }

    @Test
    void putWithAPartialBodyWipesTheOmittedFields() {
        String out = captureTrace(() -> {
            VisualHttpResource api = VisualHttpResource.serving("/users/7", user());
            api.put(Body.of("name", "Ada").and("email", "ada@example.com"));
            api.report();
        });
        assertTrue(out.contains("FIELD_LOST"), "omitted fields must be reported as lost, got:\n" + out);
        assertTrue(out.contains("holds 2 field(s)"), "only the sent fields may remain, got:\n" + out);
        assertTrue(out.contains("omitted them: 2"), "two fields were wiped, got:\n" + out);
    }

    @Test
    void patchTouchesOnlyTheFieldsItNames() {
        String out = captureTrace(() -> {
            VisualHttpResource api = VisualHttpResource.serving("/users/7", user());
            api.patch(Body.of("role", "admin"));
            api.report();
        });
        assertTrue(out.contains("PATCH_MERGED"), "expected the merge event, got:\n" + out);
        assertFalse(out.contains("FIELD_LOST"), "a patch may not wipe unmentioned fields, got:\n" + out);
        assertTrue(out.contains("holds 4 field(s)"), "every field must survive, got:\n" + out);
        assertTrue(out.contains("+1-555-0100"), "the untouched phone must still be there, got:\n" + out);
    }

    @Test
    void repeatingTheSameFullPutChangesNothing() {
        String out = captureTrace(() -> {
            VisualHttpResource api = VisualHttpResource.serving("/users/7", user());
            Body body = Body.of("name", "Ada").and("email", "ada@example.com")
                    .and("role", "admin").and("phone", "+1-555-0100");
            api.put(body);
            api.put(Body.of("name", "Ada").and("email", "ada@example.com")
                    .and("role", "admin").and("phone", "+1-555-0100"));
            api.report();
        });
        assertTrue(out.contains("IDEMPOTENT_REPEAT"), "the second PUT must be a no-op, got:\n" + out);
        assertFalse(out.contains("NON_IDEMPOTENT_REPEAT"), "a full PUT is idempotent, got:\n" + out);
    }

    @Test
    void repeatingAnAppendPatchAppendsAgain() {
        String out = captureTrace(() -> {
            VisualHttpResource api = VisualHttpResource.serving("/users/7",
                    Body.of("name", "Ada").and("tags", "internal"));
            api.patchAppend("tags", "beta");
            api.patchAppend("tags", "beta");
            api.report();
        });
        assertTrue(out.contains("NON_IDEMPOTENT_REPEAT"),
                "an append operation is not idempotent, got:\n" + out);
        assertTrue(out.contains("tags=internal,beta,beta"),
                "the value must have grown twice, got:\n" + out);
    }

    @Test
    void mergePatchNullRemovesTheMember() {
        String out = captureTrace(() -> {
            VisualHttpResource api = VisualHttpResource.serving("/users/7", user());
            api.patch(Body.of("phone", null));
            api.report();
        });
        assertTrue(out.contains("MERGE_PATCH_NULL"), "expected the removal event, got:\n" + out);
        assertTrue(out.contains("holds 3 field(s)"), "the member must be gone, got:\n" + out);
        assertFalse(out.contains("FIELD_LOST"),
                "a deliberate removal is not an accidental one, got:\n" + out);
    }

    @Test
    void putCreatesAMissingResourceButPatchCannot() {
        String out = captureTrace(() -> {
            VisualHttpResource api = VisualHttpResource.emptyAt("/users/9");
            api.patch(Body.of("role", "admin"));
            api.put(Body.of("name", "Grace").and("role", "admin"));
            api.report();
        });
        assertTrue(out.contains("PATCH_NOT_FOUND"), "a patch has nothing to merge into, got:\n" + out);
        assertTrue(out.contains("PUT_CREATED"), "PUT must create the resource, got:\n" + out);
        assertTrue(out.contains("201 Created"), "expected a 201, got:\n" + out);
        assertTrue(out.contains("holds 2 field(s)"), "the created resource must hold the body, got:\n" + out);
    }

    @Test
    void ifMatchRejectsAWriteBuiltFromAStaleCopy() {
        String out = captureTrace(() -> {
            VisualHttpResource api = VisualHttpResource.serving("/users/7", user());
            String read = api.etag();
            api.patch(Body.of("role", "owner"));
            api.putIfMatch(read, Body.of("name", "Ada")
                    .and("email", "ada@example.com")
                    .and("role", "reader")
                    .and("phone", "+1-555-0199"));
            api.report();
        });
        assertTrue(out.contains("PRECONDITION_FAILED"), "the stale write must be refused, got:\n" + out);
        assertTrue(out.contains("412 Precondition Failed"), "expected a 412, got:\n" + out);
        assertTrue(out.contains("role=owner"), "the concurrent change must survive, got:\n" + out);
    }

    @Test
    void anUnconditionalPutOverwritesTheConcurrentChange() {
        String out = captureTrace(() -> {
            VisualHttpResource api = VisualHttpResource.serving("/users/7", user());
            api.patch(Body.of("role", "owner"));
            api.put(Body.of("name", "Ada")
                    .and("email", "ada@example.com")
                    .and("role", "reader")
                    .and("phone", "+1-555-0100"));
            api.report();
        });
        assertTrue(out.contains("PUT_REPLACED"), "expected the replacement event, got:\n" + out);
        assertTrue(out.contains("role=reader"), "the stale copy must have won, got:\n" + out);
        assertFalse(out.contains("PRECONDITION_FAILED"),
                "an unconditional PUT has nothing to check, got:\n" + out);
    }

    @Test
    void everyTraceLineIsPrefixed() {
        String out = captureTrace(() -> {
            VisualHttpResource api = VisualHttpResource.serving("/users/7", user());
            api.put(Body.of("name", "Ada").and("email", "ada@example.com"));
            api.patch(Body.of("role", "admin"));
            api.patch(Body.of("role", null));
            api.patchAppend("tags", "beta");
            api.putIfMatch("v1", Body.of("name", "Ada"));
            api.report();
        });
        out.lines().forEach(line -> {
            if (!line.isEmpty()) {
                assertTrue(line.startsWith(Trace.PREFIX), "unexpected non-trace line: " + line);
            }
        });
    }
}
