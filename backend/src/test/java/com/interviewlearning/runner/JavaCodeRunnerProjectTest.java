package com.interviewlearning.runner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewlearning.config.RepoPaths;
import com.interviewlearning.topics.TopicDtos.ProjectFile;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaCodeRunnerProjectTest {

    private final RepoPaths repoPaths = new RepoPaths("");
    private final JavaCodeRunner runner = new JavaCodeRunner(repoPaths, new ObjectMapper(), 15, "128m");

    @Test
    void runsSolutionPlusHarnessAndReportsTestEvents() {
        Assumptions.assumeTrue(repoPaths.visualRuntimeJar().isPresent(),
                "visual-runtime jar not built — run ./gradlew :visual-runtime:jar first");

        List<ProjectFile> files = List.of(
                new ProjectFile("Solution.java",
                        "public class Solution { public int twice(int x) { return x * 2; } }"),
                new ProjectFile("Main.java",
                        "import visual.TestKit;\n"
                                + "public class Main {\n"
                                + "  public static void main(String[] a) {\n"
                                + "    Solution s = new Solution();\n"
                                + "    TestKit.expect(\"two\", 4, s.twice(2));\n"
                                + "    TestKit.expect(\"bad\", 7, s.twice(3));\n"
                                + "  }\n"
                                + "}")
        );

        RunResult r = runner.runProject(files, "Main");
        assertNull(r.error(), () -> "unexpected error: " + r.error());

        List<JsonNode> tests = r.traceEvents().stream()
                .filter(e -> "TEST".equals(e.path("event").asText()))
                .toList();
        assertEquals(2, tests.size(), "two test cases");
        assertEquals(true, tests.get(0).path("state").path("passed").asBoolean(), "twice(2) == 4 passes");
        assertEquals(false, tests.get(1).path("state").path("passed").asBoolean(), "twice(3) != 7 fails");
    }

    @Test
    void showcaseReferenceSolutionPassesEveryHarnessCase() throws IOException {
        Assumptions.assumeTrue(repoPaths.visualRuntimeJar().isPresent(),
                "visual-runtime jar not built");

        Path topic = topicsDir().resolve("algo-max-pair-product");
        String harness = Files.readString(topic.resolve("harness/Main.java"), StandardCharsets.UTF_8);
        String reference = "public class Solution {"
                + "  public int maxPairProduct(int[] nums) {"
                + "    int max1 = Integer.MIN_VALUE, max2 = Integer.MIN_VALUE;"
                + "    int min1 = Integer.MAX_VALUE, min2 = Integer.MAX_VALUE;"
                + "    for (int n : nums) {"
                + "      if (n > max1) { max2 = max1; max1 = n; } else if (n > max2) { max2 = n; }"
                + "      if (n < min1) { min2 = min1; min1 = n; } else if (n < min2) { min2 = n; }"
                + "    }"
                + "    return Math.max(max1 * max2, min1 * min2);"
                + "  }"
                + "}";

        RunResult r = runner.runProject(
                List.of(new ProjectFile("Solution.java", reference), new ProjectFile("Main.java", harness)),
                "Main");
        assertNull(r.error(), () -> "unexpected error: " + r.error());

        List<JsonNode> tests = r.traceEvents().stream()
                .filter(e -> "TEST".equals(e.path("event").asText()))
                .toList();
        assertEquals(6, tests.size(), "six harness cases");
        assertTrue(tests.stream().allMatch(t -> t.path("state").path("passed").asBoolean()),
                "the reference solution passes every case");
    }

    private static Path topicsDir() {
        Path p = Paths.get("").toAbsolutePath();
        while (p != null) {
            Path t = p.resolve("topics");
            if (Files.isDirectory(t)) return t;
            p = p.getParent();
        }
        throw new IllegalStateException("topics/ not found");
    }
}
