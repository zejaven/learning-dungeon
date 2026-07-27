package visual;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualEnvBindingTest {

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
    void theVariableThatOverridesJobTimeoutIsJobTimeout() {
        String[] name = new String[1];
        String out = captureTrace(() -> {
            VisualEnvBinding app = VisualEnvBinding.application();
            name[0] = app.envNameFor("job.timeout");
        });
        assertEquals("JOB_TIMEOUT", name[0], "job.timeout must convert to JOB_TIMEOUT");
        assertTrue(out.contains("BINDING_READY"), "expected the rule to be announced, got:\n" + out);
        assertTrue(out.contains("NAME_STEP"), "expected the conversion steps, got:\n" + out);
        assertTrue(out.contains("ENV_NAME_DERIVED"), "expected the derived name, got:\n" + out);
    }

    @Test
    void aDashIsDeletedRatherThanTurnedIntoAnUnderscore() {
        assertEquals("JOB_READTIMEOUT", VisualEnvBinding.envName("job.read-timeout"));
        assertEquals("SPRING_JPA_HIBERNATE_DDLAUTO",
                VisualEnvBinding.envName("spring.jpa.hibernate.ddl-auto"));
    }

    @Test
    void aListIndexIsSurroundedByUnderscores() {
        String[] name = new String[1];
        String out = captureTrace(() -> {
            VisualEnvBinding app = VisualEnvBinding.application();
            name[0] = app.envNameFor("job.targets[0].url");
        });
        assertEquals("JOB_TARGETS_0_URL", name[0], "an index is spelled _0_");
        assertTrue(out.contains("LIST_BINDING"), "expected the list rule to be explained, got:\n" + out);
    }

    @Test
    void overridingOneListElementReplacesTheWholeList() {
        String[] value = new String[1];
        String out = captureTrace(() -> {
            VisualEnvBinding app = VisualEnvBinding.application();
            app.fileProperty("job.targets[0].url", "https://staging.example/a");
            app.fileProperty("job.targets[1].url", "https://staging.example/b");
            app.export("JOB_TARGETS_0_URL", "https://prod.example/a");
            value[0] = app.resolve("job.targets[0].url");
            app.report();
        });
        assertEquals("https://prod.example/a", value[0], "the variable must win");
        assertTrue(out.contains("LIST_REPLACED"), "expected the list to be replaced, got:\n" + out);
        assertTrue(out.contains("job.targets[1].url -> JOB_TARGETS_1_URL = null (dropped)"),
                "expected the other element to be gone, got:\n" + out);
    }

    @Test
    void theVariableOverridesTheValueFromTheFile() {
        String[] value = new String[1];
        String out = captureTrace(() -> {
            VisualEnvBinding app = VisualEnvBinding.application();
            app.fileProperty("job.timeout", "60s");
            app.export("JOB_TIMEOUT", "30s");
            value[0] = app.resolve("job.timeout");
        });
        assertEquals("30s", value[0], "the environment variable must win");
        assertTrue(out.contains("PROPERTY_DECLARED"), "expected the file baseline, got:\n" + out);
        assertTrue(out.contains("VARIABLE_EXPORTED"), "expected the export, got:\n" + out);
        assertTrue(out.contains("BINDING_MATCHED"), "expected a match, got:\n" + out);
    }

    @Test
    void aMisspelledVariableChangesNothingAndSaysNothing() {
        String[] value = new String[1];
        String out = captureTrace(() -> {
            VisualEnvBinding app = VisualEnvBinding.application();
            app.fileProperty("job.timeout", "60s");
            // The dot was dropped instead of being converted.
            app.export("JOBTIMEOUT", "30s");
            value[0] = app.resolve("job.timeout");
        });
        assertEquals("60s", value[0], "a wrong name must leave the file value in place");
        assertTrue(out.contains("BINDING_MISSED"), "expected a miss, got:\n" + out);
        assertTrue(out.contains("NEAR_MISS"), "expected the near miss to be pointed out, got:\n" + out);
    }

    @Test
    void aVariableAloneIsEnoughWhenNoFileDefinesTheKey() {
        String[] value = new String[1];
        captureTrace(() -> {
            VisualEnvBinding app = VisualEnvBinding.application();
            app.export("JOB_TIMEOUT", "30s");
            value[0] = app.resolve("job.timeout");
        });
        assertEquals("30s", value[0], "a variable is a property source of its own");
    }

    @Test
    void offConventionSpellingsStillResolve() {
        String[] values = new String[3];
        captureTrace(() -> {
            VisualEnvBinding lower = VisualEnvBinding.application();
            lower.export("job_timeout", "1s");
            values[0] = lower.resolve("job.timeout");

            VisualEnvBinding verbatim = VisualEnvBinding.application();
            verbatim.export("job.timeout", "2s");
            values[1] = verbatim.resolve("job.timeout");

            VisualEnvBinding legacy = VisualEnvBinding.application();
            legacy.export("JOB_READ_TIMEOUT", "3s");
            values[2] = legacy.resolve("job.read-timeout");
        });
        assertEquals("1s", values[0], "a lowercase name still resolves");
        assertEquals("2s", values[1], "a literal dotted name still resolves");
        assertEquals("3s", values[2], "the legacy dash-as-underscore spelling still resolves");
    }

    @Test
    void getenvMatchesTheExactNameAndNothingElse() {
        String[] values = new String[2];
        String out = captureTrace(() -> {
            VisualEnvBinding app = VisualEnvBinding.application();
            app.export("JOB_TIMEOUT", "30s");
            values[0] = app.getenv("job.timeout");
            values[1] = app.getenv("JOB_TIMEOUT");
        });
        assertNull(values[0], "the JVM does not convert dotted names");
        assertEquals("30s", values[1], "the exact name is found");
        assertTrue(out.contains("SYSTEM_GETENV"), "expected the raw lookup, got:\n" + out);
    }

    @Test
    void deploymentFormsKeepTheSameVariableName() {
        String out = captureTrace(() -> {
            VisualEnvBinding app = VisualEnvBinding.application();
            app.envNameFor("job.timeout");
            app.deploymentForms("JOB_TIMEOUT", "30s");
        });
        assertTrue(out.contains("DEPLOYMENT_FORMS"), "expected the deployment spellings, got:\n" + out);
        assertTrue(out.contains("docker run -e JOB_TIMEOUT=30s"), "expected the docker form, got:\n" + out);
        assertTrue(out.contains("--job.timeout=30s"),
                "expected the command line argument to keep its dots, got:\n" + out);
    }

    @Test
    void theReportShowsWhereEveryValueCameFrom() {
        String out = captureTrace(() -> {
            VisualEnvBinding app = VisualEnvBinding.application();
            app.fileProperty("job.timeout", "60s");
            app.fileProperty("job.enabled", "true");
            app.export("JOB_TIMEOUT", "30s");
            app.resolve("job.timeout");
            app.resolve("job.enabled");
            app.report();
        });
        assertTrue(out.contains("NAMING_REPORT"), "expected the summary, got:\n" + out);
        assertTrue(out.contains("job.timeout -> JOB_TIMEOUT = 30s (env)"),
                "expected the overridden key in the report, got:\n" + out);
        assertTrue(out.contains("job.enabled -> JOB_ENABLED = true (file)"),
                "expected the untouched key in the report, got:\n" + out);
    }
}
