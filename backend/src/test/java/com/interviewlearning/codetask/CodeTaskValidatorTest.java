package com.interviewlearning.codetask;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewlearning.codetask.CodeTaskDtos.CodeTask;
import com.interviewlearning.config.RepoPaths;
import com.interviewlearning.runner.JavaCodeRunner;
import com.interviewlearning.topics.TopicDtos.Localized;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The validator has to catch what a generator gets wrong, so each test breaks a
 * copy of a known-good task in one way and checks that exactly that is reported.
 */
class CodeTaskValidatorTest {

    private static final List<String> LANGS = List.of("en", "ru");

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
        List<String> errs = validator.problems(task, LANGS);
        assertTrue(errs.isEmpty(), () -> String.join(System.lineSeparator(), errs));
    }

    @Test
    void aReferenceThatFailsItsTestsIsRejected() throws IOException {
        Files.copy(task.resolve("starter/Solution.java"), task.resolve("reference/Solution.java"),
                StandardCopyOption.REPLACE_EXISTING);
        assertReported(task, LANGS, "reference does not solve the task");
    }

    @Test
    void aWrongSolutionThatDoesNotCompileIsRejected() throws IOException {
        Path broken = task.resolve("wrong/broken");
        Files.createDirectories(broken);
        Files.writeString(broken.resolve("Solution.java"), "class Route {", StandardCharsets.UTF_8);
        assertReported(task, LANGS, "wrong/broken must be rejected by a test");
    }

    @Test
    void aWrongSolutionThatPassesIsRejected() throws IOException {
        Path lucky = task.resolve("wrong/lucky");
        Files.createDirectories(lucky);
        Files.copy(task.resolve("reference/Solution.java"), lucky.resolve("Solution.java"));
        assertReported(task, LANGS, "wrong/lucky passes every test");
    }

    @Test
    void anUnknownKindIsRejected() throws IOException {
        Path yaml = task.resolve("task.yaml");
        String text = Files.readString(yaml, StandardCharsets.UTF_8);
        Files.writeString(yaml, text.replace("kind: practice", "kind: bonus"), StandardCharsets.UTF_8);
        assertReported(task, LANGS, "kind must be 'practice' or 'final'");
    }

    @Test
    void aMissingLanguageIsRejected() {
        assertReported(task, List.of("en", "ru", "de"), "title: no text in 'de'");
    }

    @Test
    void aFolderThatIsNotAnIdIsRejected() throws IOException {
        Path renamed = Files.move(task, tmp.resolve("Vanishing Route"));
        assertReported(renamed, LANGS, "not a kebab-case id");
    }

    @Test
    void aTopicKeepsOneFinalTask() {
        assertTrue(CodeTaskValidator.topicProblems(List.of(finalTask("a"), practiceTask("b"))).isEmpty());
        List<String> errs = CodeTaskValidator.topicProblems(List.of(finalTask("a"), finalTask("b")));
        assertTrue(!errs.isEmpty() && errs.get(0).contains("at most one final"), errs::toString);
    }

    private void assertReported(Path dir, List<String> languages, String fragment) {
        List<String> errs = validator.problems(dir, languages);
        assertTrue(errs.stream().anyMatch(e -> e.contains(fragment)),
                () -> "expected '" + fragment + "' in: " + errs);
    }

    private static CodeTask finalTask(String id) {
        return new CodeTask(id, CodeTask.FINAL, 1, Localized.empty(), Localized.empty(), List.of(),
                Localized.empty(), "", "");
    }

    private static CodeTask practiceTask(String id) {
        return new CodeTask(id, CodeTask.PRACTICE, 1, Localized.empty(), Localized.empty(), List.of(),
                Localized.empty(), "", "");
    }
}
