package com.interviewlearning.generation;

import com.interviewlearning.ai.AiCliService;
import com.interviewlearning.ai.AiTask;
import com.interviewlearning.codetask.CodeTaskDtos.CodeTask;
import com.interviewlearning.config.RepoPaths;
import com.interviewlearning.lang.ContentLanguages;
import com.interviewlearning.lesson.LessonDtos.Atom;
import com.interviewlearning.topics.TopicDtos.TopicDetail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Builds the GENERATE_TASKS prompt: prompts/generate-code-tasks.md plus the
 * topic's atoms, theory, existing tasks and the draft directory to write into.
 * Tasks always carry every language the topic declares, because the validator
 * (and the contract test after it) demands exactly that.
 */
@Service
public class TasksPromptBuilder {

    private static final Logger log = LoggerFactory.getLogger(TasksPromptBuilder.class);

    private final RepoPaths repoPaths;
    private final AiCliService ai;

    public TasksPromptBuilder(RepoPaths repoPaths, AiCliService ai) {
        this.repoPaths = repoPaths;
        this.ai = ai;
    }

    public String build(TopicDetail topic, String provider, List<Atom> atoms, List<CodeTask> existing,
                        String comment, Path draft) {
        List<String> languages = topic.languages();
        String first = languages.get(0);
        StringBuilder sb = new StringBuilder(contract());

        sb.append("\n\n---\n\nLANGUAGES: ")
                .append(languages.stream().map(ContentLanguages::displayName).collect(Collectors.joining(", ")))
                .append(" — every localized field in task.yaml has exactly these keys: ")
                .append(String.join(", ", languages)).append(".\n");

        sb.append("\n---\n\nTOPIC:\n- id: ").append(topic.id()).append("\n");
        for (String lang : languages) {
            sb.append("- title (").append(lang).append("): ").append(topic.title().label(lang)).append("\n");
        }

        String model = ai.modelFor(provider, AiTask.GENERATE_TASKS);
        sb.append("\nVALUES TO SET IN task.yaml:\n")
                .append("- aiProvider: ").append(provider).append("\n")
                .append("- aiModel: ").append(model == null ? "" : model.trim()).append("\n");

        sb.append("\n---\n\nATOMS OF THE LESSON (atomId — title — summary):\n");
        for (Atom atom : atoms) {
            if (atom.capstone()) {
                continue; // the synthesis atom restates the others; a task binds to one rule
            }
            sb.append("- ").append(atom.id()).append(" — ").append(atom.title().label(first))
                    .append(" — ").append(atom.summary().label(first)).append("\n");
        }

        if (existing.isEmpty()) {
            sb.append("\nEXISTING TASKS: none. Start `order` at 1.\n");
        } else {
            int maxOrder = existing.stream().mapToInt(CodeTask::order)
                    .filter(o -> o != Integer.MAX_VALUE).max().orElse(0);
            sb.append("\nEXISTING TASKS (do not repeat their mistake; do not reuse their ids). ")
                    .append("Continue `order` from ").append(maxOrder + 1).append(":\n");
            for (CodeTask task : existing) {
                sb.append("- ").append(task.id()).append(" (atom ").append(task.atomId()).append("): ")
                        .append(task.title().label(first)).append("\n");
            }
        }

        if (comment != null && !comment.isBlank()) {
            sb.append("\n---\n\nTHE LEARNER ASKS FOR (follow it where it fits the rules above):\n")
                    .append(comment.trim()).append("\n");
        }

        for (String lang : languages) {
            String source = topic.explanation().get(lang);
            if (source != null) {
                sb.append("\n---\n\nSOURCE EXPLANATION (").append(ContentLanguages.displayName(lang))
                        .append("):\n\n").append(source).append("\n");
                break; // one language is enough to understand the topic; tasks are written fresh
            }
        }

        sb.append("\n---\n\nVISUAL-RUNTIME JAR: ")
                .append(repoPaths.visualRuntimeJar().map(p -> p.toAbsolutePath().toString()).orElse("(not built)"))
                .append("\n\nDRAFT DIRECTORY (write every task folder, or the single .none file, here):\n")
                .append(draft.toAbsolutePath());
        return sb.toString();
    }

    /** The prompt for the one repair round: same contract, plus what failed. */
    public String buildFix(String original, Map<String, List<String>> problems) {
        StringBuilder sb = new StringBuilder(original);
        sb.append("\n\n---\n\nFIX ROUND. You already wrote the draft directory above, and the app ran its ")
                .append("check on it. The task folders below FAILED. Fix their files in place so they pass ")
                .append("(reference passes every test, starter compiles and fails, every wrong/* compiles and ")
                .append("fails a test by a wrong answer). If a task cannot be made sound, delete its folder. ")
                .append("Do not touch the folders that are not listed: they passed.\n");
        problems.forEach((task, errs) -> {
            sb.append("\n").append(task).append(":\n");
            errs.forEach(e -> sb.append("- ").append(e).append("\n"));
        });
        return sb.toString();
    }

    private String contract() {
        Path file = repoPaths.promptsDir().resolve("generate-code-tasks.md");
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("generate-code-tasks.md not found at {}: {}", file, e.getMessage());
            return "Write 0-3 Java code tasks for the topic below into the draft directory given at the "
                    + "end, in the format of topics/hashmap/tasks/vanishing-route/ (task.yaml, starter/, "
                    + "reference/, harness/Main.java using TestKit.run, wrong/<name>/).";
        }
    }
}
