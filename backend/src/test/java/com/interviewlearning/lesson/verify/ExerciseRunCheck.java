package com.interviewlearning.lesson.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.interviewlearning.config.RepoPaths;
import com.interviewlearning.runner.JavaCodeRunner;
import com.interviewlearning.runner.RunResult;
import com.interviewlearning.topics.TopicDtos.ProjectFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Stage one of verifying lesson exercises whose answer only a model ever
 * vouched for: every {@code predict_output} and {@code spot_bug} snippet of a
 * roadmap module is wrapped into a {@code Main}, compiled and run twice, and a
 * {@code predict_output} whose output literally matches an option is checked
 * against the option marked correct. Everything the run cannot settle goes on
 * to stage two, {@code tools/verify/check_exercises_codex.py}.
 *
 * <p>Off by default: a lesson-wide run takes minutes. Turn it on with
 * {@code .\gradlew.bat :backend:test --tests "*ExerciseRunCheck" -Dverify.module=collections-streams}
 * ({@code -Dverify.topics=a,b} picks topics directly; {@code -Dverify.atomsRoot=<dir>}
 * reads {@code <dir>/<topic>/learning-atoms.json} instead of the topics folder,
 * for a calibration copy). Writes {@code backend/build/exercise-verify/<label>/run.json}.
 */
class ExerciseRunCheck {

    private static final List<String> TYPES = List.of("predict_output", "spot_bug");
    private static final String IMPORTS = """
            import java.util.*;
            import java.util.function.*;
            import java.util.stream.*;
            import java.util.concurrent.*;
            import java.util.concurrent.atomic.*;
            """;

    private static final Pattern TYPE_DECL = Pattern.compile(
            "^\\s*(?:@\\w+(?:\\([^)]*\\))?\\s*)*(?:(?:public|private|protected|static|final|abstract|sealed|non-sealed)\\s+)*"
                    + "(?:class|record|interface|enum)\\s+\\w+.*");
    private static final Pattern METHOD_DECL = Pattern.compile(
            "^\\s*(?:@\\w+\\s*)*(?:(?:public|private|protected|static|final|synchronized)\\s+)*"
                    + "(?:<[^>]+>\\s+)?[\\w.$<>\\[\\],? ]+\\s+(\\w+)\\s*\\([^;]*\\)\\s*(?:throws\\s+[\\w., ]+)?\\s*\\{\\s*$");
    private static final List<String> NOT_METHODS = List.of("if", "for", "while", "switch", "catch", "synchronized",
            "try", "else", "return", "new", "do");
    private static final Pattern EXCEPTION = Pattern.compile("Exception in thread \"[^\"]*\" ([\\w.$]+)");
    private static final Pattern ENV_HINT = Pattern.compile(
            "parallel|Thread|Executor|CompletableFuture|nanoTime|currentTimeMillis|identityHashCode|Random|Set\\.of|Map\\.of");

    static boolean enabled() {
        return System.getProperty("verify.module") != null || System.getProperty("verify.topics") != null;
    }

    @Test
    @EnabledIf("enabled")
    void runEveryCodeExerciseOfTheModule() throws IOException {
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        RepoPaths repoPaths = new RepoPaths("");
        JavaCodeRunner runner = new JavaCodeRunner(repoPaths, mapper, 10, "128m");
        Path root = repoPaths.repoRoot();

        String module = System.getProperty("verify.module");
        List<String> topics = System.getProperty("verify.topics") != null
                ? Arrays.stream(System.getProperty("verify.topics").split(",")).map(String::trim).toList()
                : moduleTopics(mapper, root.resolve("frontend/src/roadmap.json"), module);
        Path atomsRoot = System.getProperty("verify.atomsRoot") != null
                ? Path.of(System.getProperty("verify.atomsRoot"))
                : repoPaths.topicsDir();
        String label = module != null ? module : String.join("+", topics);

        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Integer> tally = new TreeMap<>();
        for (String topic : topics) {
            Path file = atomsRoot.resolve(topic).resolve("learning-atoms.json");
            if (!Files.isRegularFile(file)) {
                System.out.println("skip " + topic + ": no lesson");
                continue;
            }
            JsonNode atoms = mapper.readTree(file.toFile());
            for (JsonNode atom : atoms.path("atoms")) {
                for (String list : List.of("discovery", "practice")) {
                    for (JsonNode ex : atom.path(list)) {
                        if (!TYPES.contains(ex.path("type").asText())) {
                            continue;
                        }
                        Map<String, Object> row = check(runner, topic, atom.path("id").asText(), ex);
                        rows.add(row);
                        String key = row.get("group") + "/" + row.get("verdict");
                        tally.merge(key, 1, Integer::sum);
                        System.out.printf("%-28s %-40s %-13s %s%n", topic, ex.path("id").asText(),
                                row.get("group"), row.get("verdict"));
                    }
                }
            }
        }
        Path out = root.resolve("backend/build/exercise-verify").resolve(label);
        Files.createDirectories(out);
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("label", label);
        doc.put("topics", topics);
        doc.put("tally", tally);
        doc.put("exercises", rows);
        mapper.writeValue(out.resolve("run.json").toFile(), doc);
        System.out.println(rows.size() + " exercises -> " + out.resolve("run.json"));
        tally.forEach((k, v) -> System.out.println("  " + k + ": " + v));
    }

    private static Map<String, Object> check(JavaCodeRunner runner, String topic, String atomId, JsonNode ex) {
        Map<String, Object> row = new LinkedHashMap<>();
        String code = ex.path("code").asText("");
        String type = ex.path("type").asText();
        row.put("topic", topic);
        row.put("atom", atomId);
        row.put("id", ex.path("id").asText());
        row.put("type", type);
        row.put("prompt", ex.path("prompt").path("en").asText());
        row.put("code", code);
        List<Map<String, Object>> options = new ArrayList<>();
        String correctId = null;
        for (JsonNode o : ex.path("options")) {
            Map<String, Object> opt = new LinkedHashMap<>();
            opt.put("id", o.path("id").asText());
            opt.put("text", o.path("text").path("en").asText());
            opt.put("correct", o.path("correct").asBoolean());
            options.add(opt);
            if (o.path("correct").asBoolean()) {
                correctId = o.path("id").asText();
            }
        }
        row.put("options", options);
        row.put("correctId", correctId);
        row.put("envHint", ENV_HINT.matcher(code).find());

        if (!"java".equals(ex.path("codeLang").asText("java"))) {
            row.put("group", "needs-stubs");
            row.put("verdict", "not-java");
            return row;
        }
        String source = wrap(code);
        row.put("wrapped", source);
        RunResult first = runner.runProject(List.of(new ProjectFile("Main.java", source)), "Main");
        if (first.error() != null && first.error().startsWith("Compilation failed")) {
            row.put("group", "needs-stubs");
            row.put("verdict", "does-not-compile");
            row.put("compileError", first.error().lines().limit(6).reduce("", (a, b) -> a + b + "\n").trim());
            return row;
        }
        RunResult second = runner.runProject(List.of(new ProjectFile("Main.java", source)), "Main");
        String out1 = observed(first);
        String out2 = observed(second);
        row.put("output", out1);
        if (!out1.equals(out2)) {
            row.put("group", "env-dependent");
            row.put("verdict", "output-differs-between-runs");
            row.put("output2", out2);
            return row;
        }
        row.put("group", "runnable");
        if (!"predict_output".equals(type)) {
            row.put("verdict", "ran");
            return row;
        }
        if (!code.contains("System.out") && !out1.startsWith("EXCEPTION ")) {
            row.put("verdict", "prints-nothing");
            return row;
        }
        List<String> matches = new ArrayList<>();
        for (Map<String, Object> opt : options) {
            if (matchesOption(out1, (String) opt.get("text"))) {
                matches.add((String) opt.get("id"));
            }
        }
        row.put("literalMatches", matches);
        if (matches.size() == 1 && matches.get(0).equals(correctId)) {
            row.put("verdict", "confirmed");
        } else if (!matches.isEmpty() && !matches.contains(correctId)) {
            row.put("verdict", "CONTRADICTS");
        } else {
            row.put("verdict", "no-literal-match");
        }
        return row;
    }

    /**
     * What the program showed: its printed lines, and for an uncaught exception
     * a final line {@code EXCEPTION <SimpleName>} instead of the stack trace.
     */
    static String observed(RunResult r) {
        String out = r.output() == null ? "" : r.output();
        if (r.error() != null && r.error().startsWith("Execution timed out")) {
            return out.strip() + "\nTIMEOUT";
        }
        Matcher m = EXCEPTION.matcher(out);
        if (m.find()) {
            String printed = out.substring(0, m.start()).strip();
            String name = m.group(1).substring(m.group(1).lastIndexOf('.') + 1);
            return (printed.isEmpty() ? "" : printed + "\n") + "EXCEPTION " + name;
        }
        return out.strip();
    }

    /**
     * Does an option state this output? Literal options are compared after
     * joining lines with ", " or " " and collapsing spaces; an option may add
     * commentary after a dash ("false — the hash changed"). An exception is
     * matched by its class name plus everything printed before it.
     */
    static boolean matchesOption(String observed, String option) {
        List<String> lines = observed.lines().map(String::strip).filter(s -> !s.isEmpty()).toList();
        String opt = norm(option);
        if (!lines.isEmpty() && lines.get(lines.size() - 1).startsWith("EXCEPTION ")) {
            String name = lines.get(lines.size() - 1).substring("EXCEPTION ".length());
            if (!opt.contains(name.toLowerCase())) {
                return false;
            }
            return lines.subList(0, lines.size() - 1).stream().allMatch(l -> opt.contains(norm(l)));
        }
        if (lines.isEmpty() || lines.get(lines.size() - 1).equals("TIMEOUT")) {
            return false;
        }
        String literal = opt.split(" [—–-] ", 2)[0].trim();
        for (String joiner : List.of(", ", " ", "")) {
            String joined = norm(String.join(joiner, lines));
            if (joined.equals(opt) || joined.equals(literal)) {
                return true;
            }
        }
        return false;
    }

    private static String norm(String s) {
        return s.toLowerCase().replace('`', ' ').replaceAll("\\s+", " ").replaceAll("[.]$", "").trim();
    }

    /** The snippet as a compilable Main: types beside it, methods as statics, the rest in main. */
    static String wrap(String code) {
        List<String> types = new ArrayList<>();
        List<String> methods = new ArrayList<>();
        List<String> body = new ArrayList<>();
        String[] lines = code.split("\\R", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            boolean isType = TYPE_DECL.matcher(line).matches();
            Matcher method = METHOD_DECL.matcher(line);
            boolean isMethod = !isType && method.matches() && !NOT_METHODS.contains(method.group(1))
                    && !line.trim().startsWith("new ") && !line.contains("=");
            if (isType || isMethod) {
                List<String> block = new ArrayList<>();
                int depth = 0;
                boolean opened = false;
                int j = i;
                for (; j < lines.length; j++) {
                    block.add(lines[j]);
                    for (char c : lines[j].toCharArray()) {
                        if (c == '{') {
                            depth++;
                            opened = true;
                        } else if (c == '}') {
                            depth--;
                        }
                    }
                    if (opened && depth <= 0) {
                        break;
                    }
                    if (!opened && lines[j].trim().endsWith(";")) {
                        break; // e.g. a record without a body
                    }
                }
                String text = String.join("\n", block);
                if (isType) {
                    types.add(text);
                } else {
                    methods.add(text.trim().startsWith("static") || text.contains(" static ")
                            ? text : "static " + text.stripLeading());
                }
                i = j;
            } else {
                body.add(line);
            }
        }
        StringBuilder sb = new StringBuilder(IMPORTS).append('\n');
        types.forEach(t -> sb.append(t).append("\n\n"));
        sb.append("public class Main {\n");
        methods.forEach(m -> sb.append(m).append("\n\n"));
        sb.append("public static void main(String[] args) throws Exception {\n");
        body.forEach(l -> sb.append(l).append('\n'));
        sb.append("}\n}\n");
        return sb.toString();
    }

    private static List<String> moduleTopics(ObjectMapper mapper, Path roadmap, String module) throws IOException {
        JsonNode found = find(mapper.readTree(Files.readString(roadmap, StandardCharsets.UTF_8)), module);
        if (found == null) {
            throw new IllegalArgumentException("No roadmap module '" + module + "'");
        }
        for (String key : List.of("entries", "items", "topics")) {
            if (found.has(key)) {
                List<String> ids = new ArrayList<>();
                found.get(key).forEach(n -> {
                    if (n.isTextual()) {
                        ids.add(n.asText());
                    }
                });
                return ids;
            }
        }
        throw new IllegalArgumentException("Module '" + module + "' lists no topics");
    }

    private static JsonNode find(JsonNode node, String id) {
        if (node.isObject()) {
            if (id.equals(node.path("id").asText(null))) {
                return node;
            }
            for (JsonNode child : node) {
                JsonNode r = find(child, id);
                if (r != null) {
                    return r;
                }
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                JsonNode r = find(child, id);
                if (r != null) {
                    return r;
                }
            }
        }
        return null;
    }
}
