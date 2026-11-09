package com.interviewlearning.codetask;

import com.interviewlearning.ai.AiTask;
import com.interviewlearning.codetask.CodeTaskDtos.CodeTask;
import com.interviewlearning.config.RepoPaths;
import com.interviewlearning.generation.GenerationService;
import com.interviewlearning.generation.GenerationTask;
import com.interviewlearning.generation.TasksPromptBuilder;
import com.interviewlearning.lesson.LearningAtomsRepository;
import com.interviewlearning.lesson.LessonDtos.Atom;
import com.interviewlearning.lesson.LessonDtos.LearningAtoms;
import com.interviewlearning.topics.TopicDtos.TopicDetail;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Generates a topic's code tasks (practice ones and at most one final) and keeps
 * only the ones that prove themselves.
 *
 * <p>The AI writes into {@code topics/<id>/.tasks-draft/}, never into
 * {@code tasks/} directly: a draft is invisible to the app, so a half-written or
 * broken task is never shown. Every draft folder goes through
 * {@link CodeTaskValidator}; failures get one repair round with the problems
 * listed, then whatever passes moves into {@code tasks/} and the rest is dropped.
 * The AI may also decide the topic needs no code tasks: its {@code .none} note
 * is kept as {@code tasks/.none}, which marks the topic as decided.
 */
@Service
public class CodeTaskGenerator {

    public static final String DRAFT_DIR = ".tasks-draft";
    public static final String NONE_FILE = ".none";

    private final GenerationService generation;
    private final TasksPromptBuilder prompts;
    private final CodeTaskRepository tasks;
    private final CodeTaskValidator validator;
    private final LearningAtomsRepository atoms;
    private final RepoPaths repoPaths;

    public CodeTaskGenerator(GenerationService generation, TasksPromptBuilder prompts, CodeTaskRepository tasks,
                             CodeTaskValidator validator, LearningAtomsRepository atoms, RepoPaths repoPaths) {
        this.generation = generation;
        this.prompts = prompts;
        this.tasks = tasks;
        this.validator = validator;
        this.atoms = atoms;
        this.repoPaths = repoPaths;
    }

    public static String key(String topicId) {
        return "tasks:" + topicId;
    }

    /** Why this topic cannot get generated tasks, or null when it can. */
    public String refusal(TopicDetail topic) {
        if (!"java".equals(topic.domainId())) {
            return "Code tasks are generated only for Java topics.";
        }
        if (atomsOf(topic.id()).isEmpty()) {
            return "Generate the lesson first: every task is bound to one of its atoms.";
        }
        if (repoPaths.visualRuntimeJar().isEmpty()) {
            return "The visual-runtime jar is not built, so generated tasks cannot be checked.";
        }
        return null;
    }

    /** Starts (or returns the running) generation for the topic. Call {@link #refusal} first. */
    public GenerationTask start(TopicDetail topic, String provider, String comment) {
        return generation.startJob(key(topic.id()), provider, AiTask.GENERATE_TASKS,
                steps -> run(topic, provider, comment, steps));
    }

    private String run(TopicDetail topic, String provider, String comment, GenerationService.Steps steps)
            throws IOException {
        List<Atom> atomList = atomsOf(topic.id());
        List<CodeTask> existing = tasks.list(topic.id());
        Set<String> takenIds = existing.stream().map(CodeTask::id).collect(Collectors.toSet());
        boolean hasFinal = existing.stream().anyMatch(CodeTask::isFinal);

        Path draft = repoPaths.topicsDir().resolve(topic.id()).resolve(DRAFT_DIR);
        deleteRecursively(draft);
        Files.createDirectories(draft);
        try {
            String prompt = prompts.build(topic, provider, atomList, existing, comment, draft);
            if (!steps.ai(prompt)) {
                throw new IllegalStateException("The AI run failed; nothing was added.");
            }

            steps.say("Checking the generated tasks: compiling and running each one...");
            Map<String, List<String>> problems = validate(draft, topic, takenIds, hasFinal);
            if (!problems.isEmpty() && hasTaskDirs(draft)) {
                steps.say("Failed the check: " + summary(problems) + ". Asking the AI to fix them...");
                if (steps.ai(prompts.buildFix(prompt, problems))) {
                    steps.say("Checking again...");
                    problems = validate(draft, topic, takenIds, hasFinal);
                }
            }
            return promote(topic.id(), draft, problems);
        } finally {
            deleteRecursively(draft);
        }
    }

    /**
     * Problems per draft task. Besides each task's own check, the topic keeps at
     * most one final task: a final in the draft is refused when the topic
     * already has one, and only the first final of the draft may stay.
     */
    private Map<String, List<String>> validate(Path draft, TopicDetail topic, Set<String> takenIds,
                                               boolean hasFinal) throws IOException {
        Map<String, List<String>> problems = new LinkedHashMap<>();
        boolean finalTaken = hasFinal;
        for (Path dir : taskDirs(draft)) {
            String id = dir.getFileName().toString();
            List<String> errs = new ArrayList<>();
            if (takenIds.contains(id)) {
                errs.add("the id '" + id + "' is already used by an existing task of this topic");
            } else {
                errs.addAll(validator.problems(dir, topic.languages()));
                boolean isFinal = tasks.read(dir).map(CodeTask::isFinal).orElse(false);
                if (isFinal && finalTaken) {
                    errs.add("the topic already has a final task; make this one kind: practice or delete it");
                }
                finalTaken |= isFinal && errs.isEmpty();
            }
            if (!errs.isEmpty()) {
                problems.put(id, errs);
            }
        }
        return problems;
    }

    private String promote(String topicId, Path draft, Map<String, List<String>> problems) throws IOException {
        Path target = tasks.tasksDir(topicId);
        List<String> added = new ArrayList<>();
        for (Path dir : taskDirs(draft)) {
            String id = dir.getFileName().toString();
            if (problems.containsKey(id)) {
                continue;
            }
            Files.createDirectories(target);
            Files.move(dir, target.resolve(id));
            added.add(id);
        }
        String dropped = problems.isEmpty() ? "" : " Dropped " + problems.size() + ": " + summary(problems) + ".";
        if (!added.isEmpty()) {
            Files.deleteIfExists(target.resolve(NONE_FILE)); // a topic with tasks is no longer "none"
            return "Added " + added.size() + " task(s): " + String.join(", ", added) + "." + dropped;
        }
        Path none = draft.resolve(NONE_FILE);
        if (problems.isEmpty() && Files.isRegularFile(none)) {
            String reason = Files.readString(none, StandardCharsets.UTF_8).trim();
            Files.createDirectories(target);
            if (!hasTaskDirs(target)) {
                Files.writeString(target.resolve(NONE_FILE), reason, StandardCharsets.UTF_8);
            }
            return "No code tasks for this topic: " + reason;
        }
        throw new IllegalStateException(problems.isEmpty()
                ? "The AI wrote no tasks and no .none note."
                : "No task passed the check." + dropped);
    }

    /** The reason the generator gave for writing no tasks, or null. */
    public String noneReason(String topicId) {
        Path none = tasks.tasksDir(topicId).resolve(NONE_FILE);
        try {
            return Files.isRegularFile(none) ? Files.readString(none, StandardCharsets.UTF_8).trim() : null;
        } catch (IOException e) {
            return null;
        }
    }

    private List<Atom> atomsOf(String topicId) {
        return atoms.load(topicId).map(LearningAtoms::atoms).orElse(List.of());
    }

    private static String summary(Map<String, List<String>> problems) {
        return problems.entrySet().stream()
                .map(e -> e.getKey() + " (" + firstLine(e.getValue().get(0)) + ")")
                .collect(Collectors.joining("; "));
    }

    private static String firstLine(String s) {
        String line = s.lines().findFirst().orElse("");
        return line.length() > 160 ? line.substring(0, 160) + "…" : line;
    }

    private static List<Path> taskDirs(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> entries = Files.list(dir)) {
            return entries.filter(Files::isDirectory).sorted().toList();
        }
    }

    private static boolean hasTaskDirs(Path dir) throws IOException {
        return !taskDirs(dir).isEmpty();
    }

    private static void deleteRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(p);
            }
        }
    }
}
