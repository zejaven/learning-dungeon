package com.interviewlearning.generation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Stopping a generation: how the task ends, and what a stopped topic run leaves on disk. */
class GenerationStopTest {

    @TempDir
    Path topics;

    @Test
    void aStoppedTaskEndsAsStoppedWhateverItsRunReports() {
        GenerationTask task = new GenerationTask("id", "atoms:t");
        assertTrue(task.cancel());
        task.addStatus("running", "restoring the previous lesson");
        assertFalse(task.isTerminal());
        task.addStatus("done", "Lesson written.");
        assertEquals("error", task.status());
        // The killed CLI reports its exit code afterwards; the task stays as it ended.
        task.addStatus("error", "Claude Code exited with code 1.");
        assertEquals("error", task.status());
    }

    @Test
    void aFinishedTaskCannotBeStopped() {
        GenerationTask task = new GenerationTask("id", "atoms:t");
        task.addStatus("done", "Lesson written.");
        assertFalse(task.cancel());
        assertFalse(task.isCancelled());
    }

    @Test
    void aProcessStartedAfterTheStopIsKilled() throws IOException, InterruptedException {
        GenerationTask task = new GenerationTask("id", "tasks:t");
        task.cancel();
        Process longRun = new ProcessBuilder(javaBin(), "-cp", System.getProperty("java.class.path"),
                Sleeper.class.getName()).start();
        task.attachProcess(longRun);
        assertTrue(longRun.waitFor(10, TimeUnit.SECONDS), "killed at once, not after its 60s sleep");
    }

    /** Sleeps long enough to be noticeably killed. */
    public static final class Sleeper {
        public static void main(String[] args) throws InterruptedException {
            Thread.sleep(60_000);
        }
    }

    private static String javaBin() {
        return Path.of(System.getProperty("java.home"), "bin", "java").toString();
    }

    @Test
    void aStoppedTopicRunLosesTheFolderItStartedWriting() throws IOException {
        Files.createDirectories(topics.resolve("hashmap"));
        Runnable cleanup = NewTopicDirs.snapshot(topics, "catalog:plan-x", () -> List.of("catalog:plan-x"));
        Files.createDirectories(topics.resolve("broken-access-control").resolve("images"));
        Files.writeString(topics.resolve("broken-access-control").resolve("topic.yaml"), "id: x");

        cleanup.run();

        assertFalse(Files.exists(topics.resolve("broken-access-control")));
        assertTrue(Files.isDirectory(topics.resolve("hashmap")), "older topics stay");
    }

    @Test
    void theFolderStaysWhileAnotherTopicIsBeingGenerated() throws IOException {
        Runnable cleanup = NewTopicDirs.snapshot(topics, "catalog:plan-x",
                () -> List.of("catalog:plan-x", "add-topic:java", "atoms:hashmap"));
        Files.createDirectories(topics.resolve("new-topic"));

        cleanup.run();

        assertTrue(Files.isDirectory(topics.resolve("new-topic")), "it may be the other run's");
    }
}
