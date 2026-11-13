package com.interviewlearning.generation;

import com.interviewlearning.config.RepoPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Commits what a finished generation wrote, so generated content is never left
 * lying uncommitted in the working tree.
 *
 * <p>Each commit names its own paths ({@code git commit -- <paths>}), so whatever
 * else is staged or modified in the checkout (the user's own work, another run's
 * files) stays out of it. A stopped or failed run commits nothing. Nothing is
 * pushed. Off with {@code app.generation.auto-commit: false}, and silently
 * skipped outside a git checkout.
 *
 * <p>The returned line goes into the task log: it says what was committed, or why
 * not, so a failed commit is visible rather than lost in the server log.
 */
@Component
public class GitAutoCommit {

    private static final Logger log = LoggerFactory.getLogger(GitAutoCommit.class);

    /** Where a topic run may also add a {@code visual.Visual*} model and its test. */
    private static final String VISUAL_RUNTIME = "visual-runtime";

    private final Path root;
    private final boolean enabled;
    private final String gitCommand;

    public GitAutoCommit(RepoPaths repoPaths,
                         @Value("${app.generation.auto-commit:true}") boolean enabled,
                         @Value("${app.update.git-command:git}") String gitCommand) {
        this.root = repoPaths.repoRoot();
        this.enabled = enabled;
        this.gitCommand = gitCommand == null || gitCommand.isBlank() ? "git" : gitCommand.trim();
    }

    private boolean active() {
        return enabled && Files.isDirectory(root.resolve(".git"));
    }

    /**
     * The commit for a topic run, prepared before it starts: the topic folders it
     * adds, plus the visual-runtime files it creates or changes (a trace topic may
     * need a new model). Visual-runtime files already dirty at the start are not
     * the run's and stay out.
     */
    public Supplier<String> afterTopicRun(NewTopicDirs dirs) {
        if (!active()) {
            return () -> null;
        }
        Set<String> dirtyBefore = dirty(VISUAL_RUNTIME);
        return () -> {
            Optional<Set<String>> added = dirs.added();
            if (added.isEmpty()) {
                return "Not committed: another topic generation is running, so its folder cannot be told apart.";
            }
            List<String> paths = new ArrayList<>();
            for (String name : added.get()) {
                paths.add(relative(dirs.topicsDir().resolve(name)));
            }
            Set<String> runtime = dirty(VISUAL_RUNTIME);
            runtime.removeAll(dirtyBefore);
            paths.addAll(runtime);
            String what = added.get().isEmpty() ? "topic changes" : "the " + String.join(", ", added.get()) + " topic";
            return commit("generate " + what, paths);
        };
    }

    /** Commits {@code paths} (files or folders; missing ones are skipped) with {@code message}. */
    public String commitPaths(String message, Path... paths) {
        if (!active()) {
            return null;
        }
        List<String> rel = new ArrayList<>();
        for (Path p : paths) {
            if (Files.exists(p)) {
                rel.add(relative(p));
            }
        }
        return commit(message, rel);
    }

    private synchronized String commit(String message, List<String> paths) {
        if (!active()) {
            return null;
        }
        if (paths.isEmpty()) {
            return "Nothing to commit.";
        }
        List<String> add = new ArrayList<>(List.of("add", "-A", "--"));
        add.addAll(paths);
        Result added = git(add);
        if (!added.ok()) {
            return failed(added);
        }
        List<String> status = new ArrayList<>(List.of("status", "--porcelain", "--"));
        status.addAll(paths);
        Result changes = git(status);
        if (changes.ok() && changes.output().isBlank()) {
            return "Nothing to commit: the files are as committed.";
        }
        List<String> commit = new ArrayList<>(List.of("commit", "-q", "-m", message, "--"));
        commit.addAll(paths);
        Result committed = git(commit);
        if (!committed.ok()) {
            return failed(committed);
        }
        Result head = git(List.of("rev-parse", "--short", "HEAD"));
        String line = "Committed " + head.output().trim() + ": " + message;
        log.info(line);
        return line;
    }

    private static String failed(Result r) {
        String first = r.output().strip().lines().findFirst().orElse("git failed");
        log.warn("Auto-commit failed: {}", r.output().strip());
        return "Not committed: " + first;
    }

    /** Repo-relative paths under {@code dir} that git reports as modified or untracked. */
    private Set<String> dirty(String dir) {
        Set<String> paths = new LinkedHashSet<>();
        Result r = git(List.of("status", "--porcelain", "-uall", "--", dir));
        if (!r.ok()) {
            return paths;
        }
        for (String line : r.output().split("\n")) {
            if (line.length() > 3) {
                String path = line.substring(3).trim();
                int arrow = path.indexOf(" -> ");
                paths.add(arrow >= 0 ? path.substring(arrow + 4) : path);
            }
        }
        return paths;
    }

    private String relative(Path p) {
        return root.relativize(p.toAbsolutePath().normalize()).toString().replace('\\', '/');
    }

    private record Result(boolean ok, String output) {
    }

    private Result git(List<String> args) {
        List<String> cmd = new ArrayList<>();
        cmd.add(gitCommand);
        cmd.addAll(args);
        ProcessBuilder pb = new ProcessBuilder(cmd).directory(root.toFile()).redirectErrorStream(true);
        try {
            Process process = pb.start();
            StringBuilder out = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    out.append(line).append('\n');
                }
            }
            if (!process.waitFor(60, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return new Result(false, "git timed out");
            }
            return new Result(process.exitValue() == 0, out.toString());
        } catch (IOException e) {
            return new Result(false, e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Result(false, "interrupted");
        }
    }
}
