package visual;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualPropertySourcesTest {

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
    void anEnvironmentVariableBeatsApplicationProperties() {
        String[] resolved = new String[1];
        String out = captureTrace(() -> {
            VisualPropertySources env = VisualPropertySources.springBoot();
            env.packagedProperties("server.port=8080");
            env.environmentVariables("SERVER_PORT=9000");
            resolved[0] = env.getProperty("server.port");
        });
        assertEquals("9000", resolved[0], "the environment variable must win");
        assertTrue(out.contains("ENVIRONMENT_READY"), "expected the ladder to be announced, got:\n" + out);
        assertTrue(out.contains("PROPERTY_RESOLVED"), "expected a resolution, got:\n" + out);
        assertTrue(out.contains("PROPERTY_OVERRIDDEN"), "expected the file value to be shadowed, got:\n" + out);
        assertTrue(out.contains("systemEnvironment"), "expected the winning source to be named, got:\n" + out);
    }

    @Test
    void theOrderIsFixedByTheFrameworkNotByTheOrderOfCalls() {
        String[] resolved = new String[1];
        captureTrace(() -> {
            VisualPropertySources env = VisualPropertySources.springBoot();
            // Set in the opposite order: the variable first, the file last.
            env.environmentVariables("SERVER_PORT=9000");
            env.packagedProperties("server.port=8080");
            resolved[0] = env.getProperty("server.port");
        });
        assertEquals("9000", resolved[0], "setting the file last must not promote it");
    }

    @Test
    void theFullLadderResolvesToItsTopmostDefinition() {
        String[] resolved = new String[1];
        String out = captureTrace(() -> {
            VisualPropertySources env = VisualPropertySources.springBoot();
            env.packagedProperties("server.port=8080");
            env.externalProperties("server.port=8081");
            env.environmentVariables("SERVER_PORT=9000");
            env.systemProperties("server.port=9100");
            env.commandLine("--server.port=9200");
            resolved[0] = env.getProperty("server.port");
            env.precedence();
        });
        assertEquals("9200", resolved[0], "the command line is the highest rung used here");
        assertTrue(out.contains("PRECEDENCE_REPORT"), "expected the ladder report, got:\n" + out);
    }

    @Test
    void springApplicationJsonOutranksSystemPropertiesAndVariables() {
        String[] resolved = new String[1];
        captureTrace(() -> {
            VisualPropertySources env = VisualPropertySources.springBoot();
            env.environmentVariables("SERVER_PORT=9000");
            env.systemProperties("server.port=9100");
            env.springApplicationJson("server.port=7000");
            resolved[0] = env.getProperty("server.port");
        });
        assertEquals("7000", resolved[0], "SPRING_APPLICATION_JSON sits above -D and plain variables");
    }

    @Test
    void aShoutyVariableNameStillBindsToADottedKey() {
        String[] resolved = new String[1];
        String out = captureTrace(() -> {
            VisualPropertySources env = VisualPropertySources.springBoot();
            env.packagedProperties("spring.jpa.hibernate.ddl-auto=validate");
            env.environmentVariables("SPRING_JPA_HIBERNATE_DDLAUTO=none");
            resolved[0] = env.getProperty("spring.jpa.hibernate.ddl-auto");
        });
        assertEquals("none", resolved[0], "dashes are dropped and dots become underscores");
        assertTrue(out.contains("RELAXED_BINDING"), "expected relaxed binding to be reported, got:\n" + out);
    }

    @Test
    void getenvIsExactWhileTheEnvironmentIsRelaxed() {
        String[] viaSpring = new String[1];
        String[] viaJvm = new String[1];
        String out = captureTrace(() -> {
            VisualPropertySources env = VisualPropertySources.springBoot();
            env.environmentVariables("SERVER_PORT=9000");
            viaSpring[0] = env.getProperty("server.port");
            viaJvm[0] = env.getenv("server.port");
        });
        assertEquals("9000", viaSpring[0], "Spring applies relaxed binding");
        assertNull(viaJvm[0], "System.getenv matches the exact name only");
        assertTrue(out.contains("SYSTEM_GETENV"), "expected the raw OS lookup, got:\n" + out);
    }

    @Test
    void aProfileFileIsIgnoredUntilItsProfileIsActive() {
        String[] before = new String[1];
        String[] after = new String[1];
        String out = captureTrace(() -> {
            VisualPropertySources env = VisualPropertySources.springBoot();
            env.packagedProperties("spring.datasource.url=jdbc:h2:mem:dev");
            env.profileProperties("prod", "spring.datasource.url=jdbc:postgresql://db:5432/shop");
            before[0] = env.getProperty("spring.datasource.url");
            env.activateProfiles("prod");
            after[0] = env.getProperty("spring.datasource.url");
        });
        assertEquals("jdbc:h2:mem:dev", before[0], "an inactive profile file must not be read");
        assertEquals("jdbc:postgresql://db:5432/shop", after[0], "an active profile file outranks the plain one");
        assertTrue(out.contains("PROFILE_ACTIVATED"), "expected the profile switch, got:\n" + out);
    }

    @Test
    void propertySourceAnnotationAndDefaultsSitBelowApplicationProperties() {
        String[] resolved = new String[1];
        captureTrace(() -> {
            VisualPropertySources env = VisualPropertySources.springBoot();
            env.defaultProperties("app.retries=1");
            env.propertySourceAnnotation("classpath:legacy.properties", "app.retries=5");
            env.packagedProperties("app.retries=3");
            resolved[0] = env.getProperty("app.retries");
        });
        assertEquals("3", resolved[0], "@PropertySource loses to application.properties");
    }

    @Test
    void anUnknownKeyResolvesToNull() {
        String[] resolved = new String[1];
        String out = captureTrace(() -> {
            VisualPropertySources env = VisualPropertySources.springBoot();
            env.packagedProperties("server.port=8080");
            env.environmentVariables("SERVER_PROT=9000");
            resolved[0] = env.getProperty("app.feature.beta");
        });
        assertNull(resolved[0], "nothing defines the key");
        assertTrue(out.contains("PROPERTY_MISSING"), "expected a miss, got:\n" + out);
        assertTrue(out.contains("SOURCE_MISS"), "expected the non-empty sources to be walked, got:\n" + out);
    }

    @Test
    void overridingOneKeyLeavesTheRestOfTheFileInPlace() {
        String[] port = new String[1];
        String[] name = new String[1];
        String out = captureTrace(() -> {
            VisualPropertySources env = VisualPropertySources.springBoot();
            env.packagedProperties("server.port=8080", "spring.application.name=shop-api");
            env.environmentVariables("SERVER_PORT=9000");
            port[0] = env.getProperty("server.port");
            name[0] = env.getProperty("spring.application.name");
        });
        assertEquals("9000", port[0], "the overridden key comes from the variable");
        assertEquals("shop-api", name[0], "the untouched key still comes from the file");
        assertFalse(out.contains("PROPERTY_MISSING"), "neither key is missing, got:\n" + out);
    }

    @Test
    void inlinedTestPropertiesOutrankEvenTheCommandLine() {
        String[] resolved = new String[1];
        captureTrace(() -> {
            VisualPropertySources env = VisualPropertySources.springBoot();
            env.packagedProperties("server.port=8080");
            env.environmentVariables("SERVER_PORT=9000");
            env.commandLine("--server.port=9200");
            env.testProperties("server.port=0");
            resolved[0] = env.getProperty("server.port");
        });
        assertEquals("0", resolved[0], "a test's inlined properties sit at the very top");
    }
}
