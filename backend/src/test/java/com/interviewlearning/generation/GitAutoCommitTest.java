package com.interviewlearning.generation;

import com.interviewlearning.config.RepoPaths;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The auto-commit after a generation takes the run's own files and nothing else. */
class GitAutoCommitTest {

    @TempDir
    Path repo;

    private GitAutoCommit autoCommit;

    @BeforeEach
    void initRepo() throws Exception {
        git("init", "-q");
        git("config", "user.email", "test@example.com");
        git("config", "user.name", "Test");
        git("config", "commit.gpgsign", "false");
        write("topics/hashmap/topic.yaml", "id: hashmap\n");
        write("visual-runtime/src/main/java/visual/Trace.java", "class Trace {}\n");
        git("add", "-A");
        git("commit", "-q", "-m", "init");
        autoCommit = new GitAutoCommit(new RepoPaths(repo.toString()), true, "git");
    }

    @Test
    void aTopicRunCommitsItsFolderAndNewRuntimeModelOnly() throws Exception {
        // The user's own work in progress, one file staged and one not.
        write("topics/hashmap/topic.yaml", "id: hashmap\n# edited\n");
        write("notes.txt", "mine\n");
        git("add", "notes.txt");
        write("visual-runtime/src/main/java/visual/Trace.java", "class Trace { int x; }\n");

        NewTopicDirs dirs = NewTopicDirs.snapshot(repo.resolve("topics"), "catalog:plan-x",
                () -> List.of("catalog:plan-x"));
        Supplier<String> commit = autoCommit.afterTopicRun(dirs);
        write("topics/broken-access-control/topic.yaml", "id: broken-access-control\n");
        write("visual-runtime/src/main/java/visual/VisualAccessControl.java", "class VisualAccessControl {}\n");

        String line = commit.get();

        assertTrue(line.startsWith("Committed "), line);
        assertEquals(List.of(
                "topics/broken-access-control/topic.yaml",
                "visual-runtime/src/main/java/visual/VisualAccessControl.java"),
                git("show", "--name-only", "--format=", "HEAD").lines().toList());
        assertEquals("generate the broken-access-control topic", git("log", "-1", "--format=%s").trim());
        String left = git("status", "--porcelain");
        assertTrue(left.contains("A  notes.txt"), left);
        assertTrue(left.contains(" M topics/hashmap/topic.yaml"), left);
        assertTrue(left.contains(" M visual-runtime/src/main/java/visual/Trace.java"), left);
    }

    @Test
    void anUnchangedFileIsNotCommittedAgain() throws Exception {
        String line = autoCommit.commitPaths("generate the hashmap lesson",
                repo.resolve("topics/hashmap/topic.yaml"));
        assertTrue(line.startsWith("Nothing to commit"), line);
        assertEquals("init", git("log", "-1", "--format=%s").trim());
    }

    @Test
    void aRegeneratedFileIsCommitted() throws Exception {
        write("topics/hashmap/learning-atoms.json", "{}\n");
        String line = autoCommit.commitPaths("generate the hashmap lesson",
                repo.resolve("topics/hashmap/learning-atoms.json"));
        assertTrue(line.startsWith("Committed "), line);
        assertEquals("", git("status", "--porcelain"));
    }

    @Test
    void switchedOffItDoesNothing() throws Exception {
        GitAutoCommit off = new GitAutoCommit(new RepoPaths(repo.toString()), false, "git");
        write("topics/hashmap/learning-atoms.json", "{}\n");
        assertNull(off.commitPaths("x", repo.resolve("topics/hashmap/learning-atoms.json")));
        assertEquals("init", git("log", "-1", "--format=%s").trim());
    }

    private void write(String path, String text) throws IOException {
        Path p = repo.resolve(path);
        Files.createDirectories(p.getParent());
        Files.writeString(p, text, StandardCharsets.UTF_8);
    }

    private String git(String... args) throws Exception {
        List<String> cmd = new ArrayList<>(List.of("git"));
        cmd.addAll(List.of(args));
        Process p = new ProcessBuilder(cmd).directory(repo.toFile()).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, p.waitFor(), out);
        return out;
    }
}
