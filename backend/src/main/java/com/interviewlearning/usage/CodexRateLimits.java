package com.interviewlearning.usage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewlearning.config.RepoPaths;
import com.interviewlearning.usage.UsageDtos.UsageWindow;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Reads the Codex account's rate limits the way the Codex apps do: through
 * {@code codex app-server}, a JSON-RPC server on stdin/stdout, with the request
 * {@code account/rateLimits/read}. No model runs, so a reading costs no quota.
 *
 * <p>app-server is marked experimental by OpenAI, so every surprise in the
 * answer ends as "no reading" ({@link #parse} returns {@code null}) and the
 * header falls back to plain CLI status instead of showing an error.
 */
@Component
public class CodexRateLimits {

    /** Covers starting node and the backend round trip; a reading normally takes under a second. */
    static final long TIMEOUT_SECONDS = 20;

    /** A window this long or shorter is the session one, anything longer the weekly one. */
    private static final long SESSION_WINDOW_MAX_MINS = 24 * 60;

    /** The two header windows; either may be {@code null}. */
    public record Reading(UsageWindow session, UsageWindow weekly) {
    }

    private final ObjectMapper mapper;
    private final RepoPaths repoPaths;

    public CodexRateLimits(ObjectMapper mapper, RepoPaths repoPaths) {
        this.mapper = mapper;
        this.repoPaths = repoPaths;
    }

    /**
     * Starts {@code <command> app-server}, asks for the rate limits and stops it.
     *
     * @return the parsed reading, or {@code null} when the answer has no windows
     * @throws IOException on a start failure, a JSON-RPC error or a timeout
     */
    public Reading read(String command) throws IOException {
        Process process = new ProcessBuilder(command, "app-server")
                .directory(repoPaths.repoRoot().toFile())
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
        try {
            CompletableFuture<JsonNode> exchange = CompletableFuture.supplyAsync(() -> {
                try {
                    return converse(process);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            JsonNode result = exchange.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return parse(result);
        } catch (TimeoutException e) {
            throw new IOException("codex app-server did not answer in " + TIMEOUT_SECONDS + "s");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() instanceof RuntimeException r && r.getCause() != null
                    ? r.getCause() : e.getCause();
            throw cause instanceof IOException io ? io : new IOException(String.valueOf(cause.getMessage()), cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted");
        } finally {
            stop(process);
        }
    }

    private JsonNode converse(Process process) throws IOException {
        Writer in = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8);
        BufferedReader out = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
        send(in, Map.of("id", 1, "method", "initialize", "params", Map.of("clientInfo",
                Map.of("name", "java-interview-dungeon", "title", "Java Interview Dungeon", "version", "1"))));
        awaitResult(out, 1);
        send(in, Map.of("method", "initialized"));
        send(in, Map.of("id", 2, "method", "account/rateLimits/read"));
        return awaitResult(out, 2);
    }

    private void send(Writer in, Map<String, Object> message) throws IOException {
        in.write(mapper.writeValueAsString(message));
        in.write('\n');
        in.flush();
    }

    /** Skips notifications and other replies until the response with {@code id} arrives. */
    private JsonNode awaitResult(BufferedReader out, int id) throws IOException {
        String line;
        while ((line = out.readLine()) != null) {
            if (line.isBlank()) {
                continue;
            }
            JsonNode message;
            try {
                message = mapper.readTree(line);
            } catch (IOException notJson) {
                continue; // a stray log line on stdout is not our answer
            }
            if (message.path("id").asInt(-1) != id) {
                continue;
            }
            if (message.has("error")) {
                throw new IOException("codex app-server: " + message.path("error").path("message").asText("error"));
            }
            return message.path("result");
        }
        throw new IOException("codex app-server closed without answering");
    }

    /**
     * Closing stdin ends app-server; the tree kill covers the node process that
     * {@code codex.cmd} starts, which killing cmd alone would leave running.
     */
    private static void stop(Process process) {
        try {
            process.getOutputStream().close();
        } catch (IOException ignored) {
        }
        try {
            if (process.waitFor(2, TimeUnit.SECONDS)) {
                return;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
    }

    /**
     * Turns an {@code account/rateLimits/read} result into the header windows.
     * The windows are told apart by their length rather than by the field name
     * ({@code primary} is the 5-hour one today), falling back to the order
     * when a length is missing.
     *
     * @return the reading, or {@code null} when it holds no window at all
     */
    static Reading parse(JsonNode result) {
        JsonNode limits = result == null ? null : result.path("rateLimits");
        if (limits == null || !limits.isObject()) {
            return null;
        }
        List<JsonNode> windows = new ArrayList<>();
        for (String name : List.of("primary", "secondary")) {
            JsonNode w = limits.path(name);
            if (w.isObject() && w.path("usedPercent").isNumber()) {
                windows.add(w);
            }
        }
        if (windows.isEmpty()) {
            return null;
        }
        UsageWindow session = null;
        UsageWindow weekly = null;
        for (int i = 0; i < windows.size(); i++) {
            JsonNode w = windows.get(i);
            JsonNode mins = w.path("windowDurationMins");
            boolean isSession = mins.isNumber()
                    ? mins.asLong() <= SESSION_WINDOW_MAX_MINS
                    : i == 0;
            UsageWindow window = window(w);
            if (isSession && session == null) {
                session = window;
            } else if (!isSession && weekly == null) {
                weekly = window;
            }
        }
        return session == null && weekly == null ? null : new Reading(session, weekly);
    }

    private static UsageWindow window(JsonNode w) {
        double used = Math.max(0, Math.min(100, w.path("usedPercent").asDouble(0)));
        JsonNode resets = w.path("resetsAt");
        String resetsAt = resets.isNumber() ? Instant.ofEpochSecond(resets.asLong()).toString() : null;
        return new UsageWindow(used, resetsAt);
    }
}
