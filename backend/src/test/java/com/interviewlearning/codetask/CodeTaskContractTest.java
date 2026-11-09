package com.interviewlearning.codetask;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewlearning.config.RepoPaths;
import com.interviewlearning.lesson.LearningAtomsRepository;
import com.interviewlearning.lesson.LessonDtos.Atom;
import com.interviewlearning.lesson.LessonDtos.LearningAtoms;
import com.interviewlearning.runner.JavaCodeRunner;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Holds every task under {@code topics/<id>/tasks/} to {@link CodeTaskValidator},
 * the same bar the generator applies before it keeps a task.
 *
 * <p>Needs the visual-runtime jar; the Gradle {@code test} task depends on it.
 * Run just this: {@code ./gradlew :backend:test --tests "*CodeTaskContractTest"}.
 */
class CodeTaskContractTest {

    private final RepoPaths repoPaths = new RepoPaths("");
    private final CodeTaskRepository repository = new CodeTaskRepository(repoPaths);
    private final LearningAtomsRepository atoms = new LearningAtomsRepository(repoPaths, new ObjectMapper());
    private final CodeTaskValidator validator = new CodeTaskValidator(repository,
            new CodeTaskRunner(new JavaCodeRunner(repoPaths, new ObjectMapper(), 15, "128m")));

    @TestFactory
    Stream<DynamicTest> everyTaskIsWellFormedAndItsVerdictsHold() throws IOException {
        List<DynamicTest> tests = new ArrayList<>();
        try (Stream<Path> topics = Files.list(repoPaths.topicsDir())) {
            for (Path topic : topics.filter(p -> Files.isDirectory(p.resolve(CodeTaskRepository.TASKS_DIR)))
                    .sorted().toList()) {
                try (Stream<Path> dirs = Files.list(topic.resolve(CodeTaskRepository.TASKS_DIR))) {
                    for (Path dir : dirs.filter(Files::isDirectory).sorted().toList()) {
                        String name = topic.getFileName() + "/" + dir.getFileName();
                        tests.add(DynamicTest.dynamicTest(name, () -> validate(topic, dir)));
                    }
                }
            }
        }
        return tests.stream();
    }

    private void validate(Path topic, Path dir) throws IOException {
        Assumptions.assumeTrue(repoPaths.visualRuntimeJar().isPresent(),
                "visual-runtime jar not built — run ./gradlew :visual-runtime:jar first");
        Set<String> atomIds = new HashSet<>();
        atoms.load(topic.getFileName().toString()).map(LearningAtoms::atoms).orElse(List.of())
                .stream().map(Atom::id).forEach(atomIds::add);
        List<String> errs = validator.problems(dir, languages(topic), atomIds);
        assertTrue(errs.isEmpty(), () -> String.join("\n", errs));
    }

    @SuppressWarnings("unchecked")
    private static List<String> languages(Path topic) throws IOException {
        Map<String, Object> meta = new Yaml().load(
                Files.readString(topic.resolve("topic.yaml"), StandardCharsets.UTF_8));
        return (List<String>) meta.get("languages");
    }
}
