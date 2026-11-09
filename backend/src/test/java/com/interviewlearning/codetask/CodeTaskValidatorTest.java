package com.interviewlearning.codetask;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewlearning.config.RepoPaths;
import com.interviewlearning.runner.JavaCodeRunner;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The validator has to catch what a generator gets wrong, so each test breaks a
 * copy of a known-good task in one way and checks that exactly that is reported.
 */
class CodeTaskValidatorTest {

    private static final List<String> LANGS = List.of("en", "ru");
    private static final Set<String> ATOMS = Set.of("mutable-key");

    private final RepoPaths repoPaths = new RepoPaths("");
    private final CodeTaskValidator validator = new CodeTaskValidator(new CodeTaskRepository(repoPaths),
            new CodeTaskRunner(new JavaCodeRunner(repoPaths, new ObjectMapper(), 15, "128m")));

    @TempDir
    Path tmp;

    private Path task;

    @BeforeEach
    void copyGoodTask() throws IOException {
        Assumptions.assumeTrue(repoPaths.visualRuntimeJar().isPresent(),
                "visual-runtime jar not built — run ./gradlew :visual-runtime:jar first");
        Path source = repoPaths.topicsDir().resolve("hashmap/tasks/vanishing-route");
        task = tmp.resolve("vanishing-route");
        try (Stream<Path> walk = Files.walk(source)) {
            for (Path p : walk.toList()) {
                Path target = task.resolve(source.relativize(p).toString());
                if (Files.isDirectory(p)) {
                    Files.createDirectories(target);
                } else {
                    Files.copy(p, target);
                }
            }
        }
    }

    @Test
    void theOriginalPasses() {
        List<String> errs = validator.problems(task, LANGS, ATOMS);
        assertTrue(errs.isEmpty(), () -> String.join("\n", errs));
    }

    @Test
    void aReferenceThatFailsItsTestsIsRejected() throws IOException {
        Files.copy(task.resolve("starter/Solution.java"), task.resolve("reference/Solution.java"),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        assertReported("reference does not solve the task");
    }

    @Test
    void aWrongSolutionThatDoesNotCompileIsRejected() throws IOException {
        Path broken = task.resolve("wrong/broken");
        Files.createDirectories(broken);
        Files.writeString(broken.resolve("Solution.java"), "class Route {", StandardCharsets.UTF_8);
        assertReported("wrong/broken must be rejected by a test");
    }

    @Test
    void aWrongSolutionThatPassesIsRejected() throws IOException {
        Path lucky = task.resolve("wrong/lucky");
        Files.createDirectories(lucky);
        Files.copy(task.resolve("reference/Solution.java"), lucky.resolve("Solution.java"));
        assertReported("wrong/lucky passes every test");
    }

    @Test
    void anUnknownAtomIsRejected() {
        List<String> errs = validator.problems(task, LANGS, Set.of("some-other-atom"));
        assertTrue(errs.stream().anyMatch(e -> e.contains("atomId 'mutable-key'")), () -> String.join("\n", errs));
    }

    @Test
    void aMissingLanguageIsRejected() {
        List<String> errs = validator.problems(task, List.of("en", "ru", "de"), ATOMS);
        assertTrue(errs.stream().anyMatch(e -> e.contains("title: no text in 'de'")), () -> String.join("\n", errs));
    }

    @Test
    void aFolderThatIsNotAnIdIsRejected() throws IOException {
        Path renamed = Files.move(task, tmp.resolve("Vanishing Route"));
        List<String> errs = validator.problems(renamed, LANGS, ATOMS);
        assertTrue(errs.stream().anyMatch(e -> e.contains("not a kebab-case id")), () -> String.join("\n", errs));
    }

    private void assertReported(String fragment) {
        List<String> errs = validator.problems(task, LANGS, ATOMS);
        assertTrue(errs.stream().anyMatch(e -> e.contains(fragment)),
                () -> "expected '" + fragment + "' in:\n" + String.join("\n", errs));
    }
}
