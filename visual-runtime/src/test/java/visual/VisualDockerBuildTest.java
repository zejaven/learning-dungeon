package visual;

import org.junit.jupiter.api.Test;
import visual.VisualDockerBuild.Content;
import visual.VisualDockerBuild.Context;
import visual.VisualDockerBuild.Dockerfile;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualDockerBuildTest {

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

    /** The minimal Dockerfile every Spring Boot project starts with. */
    private static Dockerfile fatJarImage() {
        return Dockerfile.from("eclipse-temurin:21-jre")
                .workdir("/app")
                .copy("target/app.jar", "/app/app.jar", 45, Content.FAT_JAR)
                .expose(8080)
                .entrypoint("java -jar /app/app.jar");
    }

    /** The same image, but with the jar exploded into Spring Boot's four layers. */
    private static Dockerfile layeredImage() {
        return Dockerfile.from("eclipse-temurin:21-jre")
                .workdir("/app")
                .copy("dependencies/", "/app/", 43, Content.DEPENDENCIES)
                .copy("spring-boot-loader/", "/app/", 1, Content.STATIC)
                .copy("snapshot-dependencies/", "/app/", 0, Content.DEPENDENCIES)
                .copy("application/", "/app/", 1, Content.APPLICATION)
                .expose(8080)
                .entrypoint("java -jar /app/application.jar");
    }

    @Test
    void aFirstBuildPullsTheBaseImageAndWritesEveryLayer() {
        String out = captureTrace(() -> {
            VisualDockerBuild docker = VisualDockerBuild.daemon();
            docker.build("shop-api:1.0.0", fatJarImage());
            docker.report();
        });
        assertTrue(out.contains("BUILD_STARTED"), "expected a build to start, got:\n" + out);
        assertTrue(out.contains("BASE_IMAGE"), "expected the FROM layer, got:\n" + out);
        assertTrue(out.contains("LAYER_BUILT"), "expected a new layer, got:\n" + out);
        assertTrue(out.contains("IMAGE_BUILT"), "expected a finished image, got:\n" + out);
        assertFalse(out.contains("LAYER_CACHED"), "an empty cache cannot hit, got:\n" + out);
    }

    @Test
    void theImageIsTheBasePlusYourLayers() {
        String out = captureTrace(() -> {
            VisualDockerBuild docker = VisualDockerBuild.daemon();
            docker.build("shop-api:1.0.0", fatJarImage());
        });
        // 190 MB base + 45 MB jar; the metadata instructions add nothing.
        assertTrue(out.contains("235 MB"), "expected base + jar to be 235 MB, got:\n" + out);
    }

    @Test
    void rebuildingWithoutAChangeIsAllCacheHits() {
        String out = captureTrace(() -> {
            VisualDockerBuild docker = VisualDockerBuild.daemon();
            docker.build("shop-api:1.0.0", fatJarImage());
            docker.build("shop-api:1.0.0", fatJarImage());
            docker.report();
        });
        assertTrue(out.contains("LAYER_CACHED"), "the second build must hit the cache, got:\n" + out);
        assertFalse(out.contains("CACHE_INVALIDATED"), "nothing changed, got:\n" + out);
        assertTrue(out.contains("layers rebuilt: 2"), "only the first build writes, got:\n" + out);
    }

    @Test
    void aCodeChangeInvalidatesTheFatJarLayer() {
        String out = captureTrace(() -> {
            VisualDockerBuild docker = VisualDockerBuild.daemon();
            docker.build("shop-api:1.0.0", fatJarImage());
            docker.editCode("OrderController.java");
            docker.build("shop-api:1.0.1", fatJarImage());
            docker.report();
        });
        assertTrue(out.contains("CODE_CHANGED"), "expected the edit, got:\n" + out);
        assertTrue(out.contains("CACHE_INVALIDATED"), "the jar layer must break, got:\n" + out);
        assertTrue(out.contains("COPY target/app.jar"), "the offending layer must be named, got:\n" + out);
    }

    @Test
    void aLayeredJarKeepsTheDependencyLayerOnACodeChange() {
        String out = captureTrace(() -> {
            VisualDockerBuild docker = VisualDockerBuild.daemon();
            docker.build("shop-api:1.0.0", layeredImage());
            docker.push("shop-api:1.0.0");
            docker.editCode("OrderController.java");
            docker.build("shop-api:1.0.1", layeredImage());
            docker.push("shop-api:1.0.1");
            docker.report();
        });
        assertTrue(out.contains("COPY dependencies/ /app/ — CACHED")
                        || out.contains("COPY dependencies/ /app/ — ИЗ КЕША"),
                "the dependency layer must survive a code change, got:\n" + out);
        // First push: 190 + 43 + 1 + 0 + 1 = 235 MB. Second push: the 1 MB app layer only.
        assertTrue(out.contains("uploaded 1 MB"), "only the app layer may be re-uploaded, got:\n" + out);
        assertTrue(out.contains("pushed to the registry: 236 MB"),
                "235 MB once plus 1 MB for the change, got:\n" + out);
    }

    @Test
    void aFatJarReUploadsEverythingOnEveryCodeChange() {
        String out = captureTrace(() -> {
            VisualDockerBuild docker = VisualDockerBuild.daemon();
            docker.build("shop-api:1.0.0", fatJarImage());
            docker.push("shop-api:1.0.0");
            docker.editCode("OrderController.java");
            docker.build("shop-api:1.0.1", fatJarImage());
            docker.push("shop-api:1.0.1");
            docker.report();
        });
        assertTrue(out.contains("uploaded 45 MB"), "the whole jar goes again, got:\n" + out);
        assertTrue(out.contains("pushed to the registry: 280 MB"),
                "235 MB once plus the whole 45 MB jar again, got:\n" + out);
    }

    @Test
    void addingADependencyAlsoInvalidatesTheDependencyLayer() {
        String out = captureTrace(() -> {
            VisualDockerBuild docker = VisualDockerBuild.daemon();
            docker.build("shop-api:1.0.0", layeredImage());
            docker.addDependency("spring-boot-starter-actuator");
            docker.build("shop-api:1.1.0", layeredImage());
            docker.report();
        });
        assertTrue(out.contains("DEPENDENCY_ADDED"), "expected the pom change, got:\n" + out);
        assertTrue(out.contains("CACHE_INVALIDATED"), "the dependency layer must break, got:\n" + out);
        assertTrue(out.contains("COPY dependencies/"), "the offending layer must be named, got:\n" + out);
    }

    @Test
    void aMultiStageBuildThrowsTheBuildToolingAway() {
        String out = captureTrace(() -> {
            VisualDockerBuild docker = VisualDockerBuild.daemon();
            docker.build("shop-api:1.0.0", multiStage());
            docker.report();
        });
        assertTrue(out.contains("STAGE_DISCARDED"), "the builder stage must be dropped, got:\n" + out);
        assertTrue(out.contains("builder"), "the discarded stage must be named, got:\n" + out);
        // Only the runtime stage ships: 190 MB base + the 45 MB jar copied across.
        assertTrue(out.contains("Image shop-api:1.0.0 built: 235 MB"),
                "the builder stage must not count, got:\n" + out);
    }

    @Test
    void aMultiStageBuildStillCachesTheDependencyDownload() {
        String out = captureTrace(() -> {
            VisualDockerBuild docker = VisualDockerBuild.daemon();
            docker.build("shop-api:1.0.0", multiStage());
            docker.editCode("OrderController.java");
            docker.build("shop-api:1.0.1", multiStage());
            docker.report();
        });
        assertTrue(out.contains("RUN mvn -B dependency:go-offline — CACHED")
                        || out.contains("RUN mvn -B dependency:go-offline — ИЗ КЕША"),
                "copying pom.xml first must keep the download cached, got:\n" + out);
        assertTrue(out.contains("CACHE_INVALIDATED"), "the sources must break the cache, got:\n" + out);
        assertTrue(out.contains("COPY src/ /build/src/"), "the break must start at the sources, got:\n" + out);
    }

    private static Dockerfile multiStage() {
        return Dockerfile.from("maven:3.9-eclipse-temurin-21", "builder")
                .workdir("/build")
                .copy("pom.xml", "/build/pom.xml", 0, Content.BUILD_FILE)
                .run("mvn -B dependency:go-offline", 300)
                .copy("src/", "/build/src/", 1, Content.SOURCE_TREE)
                .run("mvn -B package -DskipTests", 45)
                .stage("eclipse-temurin:21-jre")
                .workdir("/app")
                .copyFrom("builder", "/build/target/app.jar", "/app/app.jar", 45)
                .expose(8080)
                .entrypoint("java -jar /app/app.jar");
    }

    @Test
    void dockerignoreShrinksTheContextThatIsSent() {
        String out = captureTrace(() -> {
            VisualDockerBuild docker = VisualDockerBuild.daemon(Context.of()
                    .file("pom.xml", 0)
                    .file("src/", 1)
                    .file("target/", 240)
                    .file(".git/", 90));
            docker.dockerignore("target/", ".git/");
            docker.build("shop-api:1.0.0", multiStage());
            docker.report();
        });
        assertTrue(out.contains("CONTEXT_IGNORED"), "expected the exclusion, got:\n" + out);
        assertTrue(out.contains("kept 330 MB out of the context"),
                "240 + 90 MB must stay home, got:\n" + out);
        assertTrue(out.contains("build context sent: 1 MB"), "only src/ and pom.xml, got:\n" + out);
    }

    @Test
    void anImageWithoutAUserInstructionRunsAsRoot() {
        String out = captureTrace(() -> {
            VisualDockerBuild docker = VisualDockerBuild.daemon();
            docker.build("shop-api:1.0.0", fatJarImage());
            docker.run("shop-api:1.0.0", 512);
        });
        assertTrue(out.contains("CONTAINER_STARTED"), "expected the container, got:\n" + out);
        assertTrue(out.contains("RUNS_AS_ROOT"), "no USER means root, got:\n" + out);
    }

    @Test
    void aUserInstructionRemovesTheRootWarningAndTheJvmSeesTheLimit() {
        String out = captureTrace(() -> {
            VisualDockerBuild docker = VisualDockerBuild.daemon();
            docker.build("shop-api:1.0.0", Dockerfile.from("eclipse-temurin:21-jre")
                    .workdir("/app")
                    .copy("target/app.jar", "/app/app.jar", 45, Content.FAT_JAR)
                    .run("useradd --system app", 0)
                    .user("app")
                    .expose(8080)
                    .entrypoint("java -jar /app/app.jar"));
            docker.run("shop-api:1.0.0", 512);
        });
        assertFalse(out.contains("RUNS_AS_ROOT"), "USER app was set, got:\n" + out);
        // 25% of 512 MB is the JVM's default share of a container limit.
        assertTrue(out.contains("128 MB of heap"), "expected the default heap share, got:\n" + out);
    }

    @Test
    void anExplicitMaxRamPercentageChangesTheHeapTheJvmTakes() {
        String out = captureTrace(() -> {
            VisualDockerBuild docker = VisualDockerBuild.daemon();
            docker.build("shop-api:1.0.0", Dockerfile.from("eclipse-temurin:21-jre")
                    .workdir("/app")
                    .copy("target/app.jar", "/app/app.jar", 45, Content.FAT_JAR)
                    .env("JAVA_TOOL_OPTIONS", "-XX:MaxRAMPercentage=75")
                    .user("app")
                    .entrypoint("java -jar /app/app.jar"));
            docker.run("shop-api:1.0.0", 512);
        });
        assertTrue(out.contains("MaxRAMPercentage=75"), "the image must set it, got:\n" + out);
        assertTrue(out.contains("384 MB of heap"), "75% of 512 MB, got:\n" + out);
    }

    @Test
    void everyTraceLineIsPrefixed() {
        String out = captureTrace(() -> {
            VisualDockerBuild docker = VisualDockerBuild.daemon();
            docker.dockerignore("target/");
            docker.build("shop-api:1.0.0", layeredImage());
            docker.push("shop-api:1.0.0");
            docker.editCode("OrderController.java");
            docker.addDependency("spring-boot-starter-actuator");
            docker.build("shop-api:1.1.0", layeredImage());
            docker.push("shop-api:1.1.0");
            docker.run("shop-api:1.1.0", 512);
            docker.report();
        });
        out.lines().forEach(line -> {
            if (!line.isEmpty()) {
                assertTrue(line.startsWith(Trace.PREFIX), "unexpected non-trace line: " + line);
            }
        });
    }
}
