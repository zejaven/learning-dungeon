package com.interviewlearning.claude;

import com.interviewlearning.config.RepoPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Runs the Claude Code CLI in headless ({@code -p}) mode and streams its
 * stream-json output to the browser via Server-Sent Events. The prompt is fed
 * through stdin to avoid command-line length and quoting limits.
 *
 * <p>Each raw stream-json line is forwarded as an SSE {@code claude} event so
 * the frontend can render assistant text and tool activity as it arrives. A
 * final {@code done} event carries the exit status.
 */
@Service
public class ClaudeCodeService {

    private static final Logger log = LoggerFactory.getLogger(ClaudeCodeService.class);

    private final RepoPaths repoPaths;
    private final String claudeCommand;
    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "claude-stream");
        t.setDaemon(true);
        return t;
    });

    public ClaudeCodeService(RepoPaths repoPaths,
                             @Value("${app.claude.command:claude}") String claudeCommand) {
        this.repoPaths = repoPaths;
        this.claudeCommand = claudeCommand;
    }

    /**
     * Streams a Claude run.
     *
     * @param prompt    the full prompt, fed via stdin
     * @param extraArgs CLI args added after the base headless/stream args
     *                  (e.g. permission flags for the file-writing add-topic flow)
     */
    public SseEmitter stream(String prompt, List<String> extraArgs) {
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L); // 30 min
        executor.submit(() -> runProcess(buildCommand(extraArgs), prompt, emitter));
        return emitter;
    }

    /** Receives Claude output decoupled from any single client connection. */
    public interface Sink {
        void claude(String line);

        void status(String status, String message);
    }

    /**
     * Runs a Claude generation in the background, streaming output to {@code sink}.
     * Unlike {@link #stream}, a disconnected client never aborts the process — the
     * run completes on its own and the sink (a {@code GenerationTask}) buffers
     * output for late or reconnecting subscribers.
     */
    public void runDetached(String prompt, List<String> extraArgs, Sink sink) {
        executor.submit(() -> runProcessSink(buildCommand(extraArgs), prompt, sink));
    }

    private List<String> buildCommand(List<String> extraArgs) {
        List<String> command = new ArrayList<>();
        command.add(claudeCommand);
        command.add("-p");
        command.add("--output-format");
        command.add("stream-json");
        command.add("--verbose");
        command.add("--include-partial-messages");
        command.addAll(extraArgs);
        return command;
    }

    private void runProcessSink(List<String> command, String prompt, Sink sink) {
        ProcessBuilder pb = new ProcessBuilder(command)
                .directory(repoPaths.repoRoot().toFile())
                .redirectErrorStream(false);
        Process process;
        try {
            sink.status("running", "Starting Claude Code…");
            process = pb.start();
        } catch (IOException e) {
            log.error("Failed to start Claude CLI", e);
            sink.status("error", "Could not start Claude CLI: " + e.getMessage()
                    + " (is '" + claudeCommand + "' on PATH?)");
            return;
        }

        try (OutputStream stdin = process.getOutputStream()) {
            stdin.write(prompt.getBytes(StandardCharsets.UTF_8));
            stdin.flush();
        } catch (IOException e) {
            log.warn("Failed writing prompt to Claude stdin: {}", e.getMessage());
        }

        Thread stderrDrain = new Thread(() -> {
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    log.debug("[claude stderr] {}", line);
                }
            } catch (IOException ignored) {
            }
        }, "claude-stderr");
        stderrDrain.setDaemon(true);
        stderrDrain.start();

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (!line.isBlank()) {
                    sink.claude(line);
                }
            }
            int exit = process.waitFor();
            sink.status(exit == 0 ? "done" : "error",
                    exit == 0 ? "Completed." : "Claude exited with code " + exit + ".");
        } catch (IOException e) {
            sink.status("error", "Stream error: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void runProcess(List<String> command, String prompt, SseEmitter emitter) {
        ProcessBuilder pb = new ProcessBuilder(command)
                .directory(repoPaths.repoRoot().toFile())
                .redirectErrorStream(false);
        Process process;
        try {
            sendStatus(emitter, "running", "Starting Claude Code…");
            process = pb.start();
        } catch (IOException e) {
            log.error("Failed to start Claude CLI", e);
            sendStatus(emitter, "error", "Could not start Claude CLI: " + e.getMessage()
                    + " (is '" + claudeCommand + "' on PATH?)");
            emitter.complete();
            return;
        }

        // Feed the prompt via stdin and close it.
        try (OutputStream stdin = process.getOutputStream()) {
            stdin.write(prompt.getBytes(StandardCharsets.UTF_8));
            stdin.flush();
        } catch (IOException e) {
            log.warn("Failed writing prompt to Claude stdin: {}", e.getMessage());
        }

        // Drain stderr so the process never blocks; log it.
        Thread stderrDrain = new Thread(() -> {
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    log.debug("[claude stderr] {}", line);
                }
            } catch (IOException ignored) {
            }
        }, "claude-stderr");
        stderrDrain.setDaemon(true);
        stderrDrain.start();

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                try {
                    emitter.send(SseEmitter.event().name("claude").data(line));
                } catch (IOException e) {
                    // Client disconnected; stop the run.
                    log.debug("SSE client disconnected, killing Claude run");
                    process.destroy();
                    break;
                }
            }
            int exit = process.waitFor();
            sendStatus(emitter, exit == 0 ? "done" : "error",
                    exit == 0 ? "Completed." : "Claude exited with code " + exit + ".");
        } catch (IOException e) {
            sendStatus(emitter, "error", "Stream error: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            emitter.complete();
        }
    }

    private void sendStatus(SseEmitter emitter, String status, String message) {
        try {
            emitter.send(SseEmitter.event()
                    .name("status")
                    .data("{\"status\":\"" + status + "\",\"message\":\""
                            + message.replace("\"", "'") + "\"}"));
        } catch (IOException ignored) {
        }
    }
}
