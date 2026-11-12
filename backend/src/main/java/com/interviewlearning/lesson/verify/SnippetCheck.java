package com.interviewlearning.lesson.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.interviewlearning.runner.JavaCodeRunner;
import com.interviewlearning.runner.RunResult;
import com.interviewlearning.topics.TopicDtos.ProjectFile;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Runs the code of a lesson exercise and compares what it prints with the
 * options. A snippet is a fragment written for a person (no imports, no
 * {@code main}), so it is first wrapped into a {@code Main}: common imports,
 * declared types beside it, declared methods as statics, the rest in
 * {@code main}. It runs twice; output that differs between the runs depends on
 * the environment and is not compared.
 *
 * <p>Only a {@code predict_output} whose output LITERALLY matches an option can
 * be settled: {@code confirmed} when that is the marked option,
 * {@code CONTRADICTS} when it is another one. Options in prose, snippets that do
 * not compile as written, and errors of reasoning are out of its reach.
 *
 * <p>Used after every lesson generation ({@code lesson/LessonGenerator}) and by
 * the module-wide test tool {@code ExerciseRunCheck}.
 */
public final class SnippetCheck {

    public static final String RUNNABLE = "runnable";
    public static final String NEEDS_STUBS = "needs-stubs";
    public static final String ENV_DEPENDENT = "env-dependent";

    public static final String CONFIRMED = "confirmed";
    public static final String CONTRADICTS = "CONTRADICTS";
    public static final String NO_LITERAL_MATCH = "no-literal-match";

    /**
     * @param group          {@link #RUNNABLE}, {@link #NEEDS_STUBS} or {@link #ENV_DEPENDENT}
     * @param verdict        {@link #CONFIRMED}, {@link #CONTRADICTS}, {@link #NO_LITERAL_MATCH},
     *                       or why nothing was compared ({@code not-java}, {@code does-not-compile},
     *                       {@code output-differs-between-runs}, {@code prints-nothing}, {@code ran})
     * @param output         what the first run showed, see {@link #observed}; null when it did not run
     * @param output2        the second run's output when it differed; otherwise null
     * @param literalMatches ids of the options that state the output
     * @param correctId      id of the option marked correct
     * @param wrapped        the compiled source; null for non-Java code
     * @param compileError   the first lines of the compiler error, when it did not compile
     */
    public record Verdict(String group, String verdict, String output, String output2,
                          List<String> literalMatches, String correctId, String wrapped, String compileError) {

        public boolean contradicts() {
            return CONTRADICTS.equals(verdict);
        }
    }

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

    private SnippetCheck() {
    }

    /** Is this exercise one the check can settle: a predict_output with Java code? */
    public static boolean checkable(JsonNode exercise) {
        return "predict_output".equals(exercise.path("type").asText())
                && !exercise.path("code").asText("").isBlank()
                && "java".equals(exercise.path("codeLang").asText("java"));
    }

    /** Runs the exercise's code and compares the output with its options (English text). */
    public static Verdict check(JavaCodeRunner runner, JsonNode exercise) {
        String code = exercise.path("code").asText("");
        String type = exercise.path("type").asText();
        String correctId = null;
        List<String[]> options = new ArrayList<>();
        for (JsonNode o : exercise.path("options")) {
            options.add(new String[]{o.path("id").asText(), o.path("text").path("en").asText()});
            if (o.path("correct").asBoolean()) {
                correctId = o.path("id").asText();
            }
        }
        if (!"java".equals(exercise.path("codeLang").asText("java"))) {
            return new Verdict(NEEDS_STUBS, "not-java", null, null, List.of(), correctId, null, null);
        }
        String source = wrap(code);
        RunResult first = runner.runProject(List.of(new ProjectFile("Main.java", source)), "Main");
        if (first.error() != null && first.error().startsWith("Compilation failed")) {
            String error = first.error().lines().limit(6).reduce("", (a, b) -> a + b + "\n").trim();
            return new Verdict(NEEDS_STUBS, "does-not-compile", null, null, List.of(), correctId, source, error);
        }
        RunResult second = runner.runProject(List.of(new ProjectFile("Main.java", source)), "Main");
        String out1 = observed(first);
        String out2 = observed(second);
        if (!out1.equals(out2)) {
            return new Verdict(ENV_DEPENDENT, "output-differs-between-runs", out1, out2, List.of(), correctId,
                    source, null);
        }
        if (!"predict_output".equals(type)) {
            return new Verdict(RUNNABLE, "ran", out1, null, List.of(), correctId, source, null);
        }
        if (!code.contains("System.out") && !out1.startsWith("EXCEPTION ")) {
            return new Verdict(RUNNABLE, "prints-nothing", out1, null, List.of(), correctId, source, null);
        }
        List<String> matches = new ArrayList<>();
        for (String[] opt : options) {
            if (matchesOption(out1, opt[1])) {
                matches.add(opt[0]);
            }
        }
        String verdict;
        if (matches.size() == 1 && matches.get(0).equals(correctId)) {
            verdict = CONFIRMED;
        } else if (!matches.isEmpty() && !matches.contains(correctId)) {
            verdict = CONTRADICTS;
        } else {
            verdict = NO_LITERAL_MATCH;
        }
        return new Verdict(RUNNABLE, verdict, out1, null, List.copyOf(matches), correctId, source, null);
    }

    /**
     * What the program showed: its printed lines, and for an uncaught exception
     * a final line {@code EXCEPTION <SimpleName>} instead of the stack trace.
     */
    public static String observed(RunResult r) {
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
    public static boolean matchesOption(String observed, String option) {
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
    public static String wrap(String code) {
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
}
