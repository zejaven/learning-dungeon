package com.interviewlearning.system;

import com.interviewlearning.config.RepoPaths;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Self-update / restart support for the packaged (tray-launched) app.
 *
 * <p>A running JVM cannot cleanly rebuild and replace its own jar (on Windows it
 * even holds the jar file open), so the actual rebuild+restart is done by the
 * tray supervisor: {@link #requestUpdate} writes a {@code launcher/update.flag}
 * sentinel and exits the JVM; {@code tray.ps1} sees the flagged exit and hands
 * off to {@code launcher/update.ps1}, which (optionally) pulls from GitHub,
 * runs {@code launcher/build-app.ps1} and relaunches. The frontend meanwhile
 * polls {@link #status} and reloads once {@code bootId} changes.
 *
 * <p>Capabilities are detected so the UI can hide/disable actions that cannot
 * work in a given deployment (e.g. a jar-only copy without the source tree or
 * without a git checkout).
 */
@Service
public class SystemService {

    private static final Logger log = LoggerFactory.getLogger(SystemService.class);

    /** Unique per process start; a change signals that the server was restarted. */
    private final String bootId = UUID.randomUUID().toString();

    private final RepoPaths repoPaths;
    private final String gitCommand;
    private final long fetchIntervalMs;

    /** Whether the JVM was launched by the tray supervisor (only then can it self-restart). */
    private final boolean supervised;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "system-fetch");
        t.setDaemon(true);
        return t;
    });

    /** -1 = unknown (no upstream / fetch failed); otherwise commits upstream is ahead of local. */
    private volatile int behind = -1;
    private volatile String version = "";

    public SystemService(RepoPaths repoPaths,
                         @Value("${app.update.git-command:git}") String gitCommand,
                         @Value("${app.update.fetch-interval-ms:3600000}") long fetchIntervalMs,
                         @Value("${app.launcher:}") String launcher) {
        this.repoPaths = repoPaths;
        this.gitCommand = (gitCommand == null || gitCommand.isBlank()) ? "git" : gitCommand.trim();
        this.fetchIntervalMs = fetchIntervalMs;
        this.supervised = "tray".equalsIgnoreCase(launcher == null ? "" : launcher.trim());
    }

    @PostConstruct
    void start() {
        version = shortHead();
        if (isGitRepo()) {
            // First fetch shortly after boot (don't slow startup), then on an interval.
            scheduler.schedule(this::refreshBehind, 10, TimeUnit.SECONDS);
            scheduler.scheduleWithFixedDelay(this::refreshBehind,
                    fetchIntervalMs, fetchIntervalMs, TimeUnit.MILLISECONDS);
        }
    }

    @PreDestroy
    void stop() {
        scheduler.shutdownNow();
    }

    // --- status -------------------------------------------------------------

    public record Status(
            boolean supervised,
            boolean canRebuild,
            boolean canPull,
            int behind,
            String version,
            String bootId) {
    }

    public Status status() {
        return new Status(supervised, canRebuild(), canPull(), behind, version, bootId);
    }

    /** Source tree present, so {@code build-app.ps1} can rebuild the jar. */
    public boolean canRebuild() {
        Path root = repoPaths.repoRoot();
        return Files.exists(root.resolve("launcher").resolve("build-app.ps1"))
                && Files.exists(root.resolve("gradlew.bat"));
    }

    /** A git checkout with a configured upstream, so "Update" can pull from GitHub. */
    public boolean canPull() {
        return isGitRepo() && hasUpstream();
    }

    public boolean supervised() {
        return supervised;
    }

    // --- update -------------------------------------------------------------

    /**
     * Writes the update sentinel and schedules a JVM exit; the tray supervisor
     * performs the rebuild/restart. Only valid under the tray, and pulling only
     * when a git upstream exists.
     *
     * @return null on success, or a message explaining why the request was refused.
     */
    public String requestUpdate(boolean pull) {
        if (!supervised) {
            return "Not running under the tray launcher; rebuild/restart is unavailable.";
        }
        if (!canRebuild()) {
            return "Source tree not found next to the app; cannot rebuild.";
        }
        if (pull && !canPull()) {
            return "No git checkout with an upstream; cannot pull from GitHub.";
        }
        Path flag = repoPaths.repoRoot().resolve("launcher").resolve("update.flag");
        try {
            Files.createDirectories(flag.getParent());
            Files.writeString(flag, "pull=" + pull + System.lineSeparator(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Could not write update flag {}: {}", flag, e.getMessage());
            return "Could not write update flag: " + e.getMessage();
        }
        log.info("Update requested (pull={}); exiting for the tray to rebuild.", pull);
        // Let the HTTP response flush before the process dies.
        Thread exit = new Thread(() -> {
            try {
                Thread.sleep(700);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            System.exit(0);
        }, "system-update-exit");
        exit.setDaemon(false);
        exit.start();
        return null;
    }

    // --- git helpers --------------------------------------------------------

    private boolean isGitRepo() {
        return Files.isDirectory(repoPaths.repoRoot().resolve(".git"));
    }

    private boolean hasUpstream() {
        return git("rev-parse", "--abbrev-ref", "--symbolic-full-name", "@{u}").ok();
    }

    private String shortHead() {
        GitResult r = git("rev-parse", "--short", "HEAD");
        return r.ok() ? r.output().trim() : "";
    }

    /** Fetches from the remote and recomputes how many commits upstream is ahead. */
    private void refreshBehind() {
        try {
            git("fetch", "--quiet");
            version = shortHead();
            GitResult r = git("rev-list", "--count", "HEAD..@{u}");
            if (r.ok()) {
                try {
                    behind = Integer.parseInt(r.output().trim());
                    return;
                } catch (NumberFormatException ignored) {
                    // fall through to unknown
                }
            }
            behind = -1;
        } catch (RuntimeException e) {
            log.debug("git fetch/behind failed: {}", e.getMessage());
            behind = -1;
        }
    }

    private record GitResult(boolean ok, String output) {
    }

    private GitResult git(String... args) {
        List<String> cmd = new ArrayList<>();
        cmd.add(gitCommand);
        cmd.addAll(List.of(args));
        ProcessBuilder pb = new ProcessBuilder(cmd)
                .directory(repoPaths.repoRoot().toFile())
                .redirectErrorStream(true);
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
            if (!process.waitFor(20, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return new GitResult(false, out.toString());
            }
            return new GitResult(process.exitValue() == 0, out.toString());
        } catch (IOException e) {
            return new GitResult(false, e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new GitResult(false, "interrupted");
        }
    }
}
