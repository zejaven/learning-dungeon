package com.interviewlearning.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewlearning.ai.AiDtos.AiProviderStatus;
import com.interviewlearning.ai.AiDtos.AiProvidersResponse;
import com.interviewlearning.config.RepoPaths;
import com.interviewlearning.system.KeepAwakeService;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Runs the selected coding-agent CLI (Claude Code, Codex CLI, or Kimi Code CLI)
 * behind a small common interface. The frontend receives normalized JSON lines
 * over SSE, so it does not need to understand each provider's stream format.
 */
@Service
public class AiCliService {

    private static final Logger log = LoggerFactory.getLogger(AiCliService.class);
    private static final long STREAM_TIMEOUT_MILLIS = 30 * 60 * 1000L;
    private static final long RESULT_TIMEOUT_MINUTES = 4;

    private final RepoPaths repoPaths;
    private final ObjectMapper mapper;
    private final KeepAwakeService keepAwake;
    private final Map<String, ProviderConfig> providers;
    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "ai-cli-stream");
        t.setDaemon(true);
        return t;
    });

    private final Map<String, ResolvedCommand> resolvedCache = new LinkedHashMap<>();
    /** Configured model name (possibly an alias) -> concrete id reported by the CLI. */
    private final Map<String, String> resolvedModels = new ConcurrentHashMap<>();

    public AiCliService(
            RepoPaths repoPaths,
            ObjectMapper mapper,
            KeepAwakeService keepAwake,
            @Value("${app.ai.claude.command:${app.claude.command:claude}}") String claudeCommand,
            @Value("${app.ai.claude.assistant-model:${app.claude.assistant-model:sonnet}}")
            String claudeAssistantModel,
            @Value("${app.ai.claude.generate-model:${app.claude.generate-model:opus}}")
            String claudeGenerateModel,
            @Value("${app.ai.claude.generate-permission-mode:${app.claude.generate-permission-mode:bypassPermissions}}")
            String claudePermissionMode,
            @Value("${app.ai.codex.command:codex}") String codexCommand,
            @Value("${app.ai.codex.assistant-model:gpt-5.4-mini}") String codexAssistantModel,
            @Value("${app.ai.codex.generate-model:gpt-5.5}") String codexGenerateModel,
            @Value("${app.ai.codex.assistant-effort:medium}") String codexAssistantEffort,
            @Value("${app.ai.codex.generate-effort:xhigh}") String codexGenerateEffort,
            @Value("${app.ai.kimi.command:kimi}") String kimiCommand,
            @Value("${app.ai.kimi.assistant-model:kimi-k2.7-code-highspeed}") String kimiAssistantModel,
            @Value("${app.ai.kimi.generate-model:kimi-k2.7-code}") String kimiGenerateModel) {
        this.repoPaths = repoPaths;
        this.mapper = mapper;
        this.keepAwake = keepAwake;
        this.providers = Map.of(
                "claude", new ProviderConfig(
                        "claude",
                        "Claude",
                        ProviderKind.CLAUDE,
                        claudeCommand,
                        claudeAssistantModel,
                        claudeGenerateModel,
                        "https://claude.com/claude-code",
                        claudePermissionMode,
                        "",
                        ""),
                "codex", new ProviderConfig(
                        "codex",
                        "Codex",
                        ProviderKind.CODEX,
                        codexCommand,
                        codexAssistantModel,
                        codexGenerateModel,
                        "https://developers.openai.com/codex/",
                        "",
                        codexAssistantEffort,
                        codexGenerateEffort),
                "kimi", new ProviderConfig(
                        "kimi",
                        "Kimi",
                        ProviderKind.KIMI,
                        kimiCommand,
                        kimiAssistantModel,
                        kimiGenerateModel,
                        "https://moonshotai.github.io/kimi-cli/en/guides/getting-started.html",
                        "",
                        "",
                        "")
        );
    }

    public AiProvidersResponse providers() {
        List<AiProviderStatus> statuses = providers.keySet().stream()
                .sorted()
                .map(this::status)
                .toList();
        String defaultProvider = statuses.stream()
                .filter(AiProviderStatus::available)
                .map(AiProviderStatus::id)
                .sorted()
                .findFirst()
                .orElse("claude");
        return new AiProvidersResponse(defaultProvider, statuses);
    }

    public AiProviderStatus status(String providerId) {
        ProviderConfig cfg = config(providerId);
        ResolvedCommand resolved = resolve(cfg);
        return new AiProviderStatus(
                cfg.id(),
                cfg.label(),
                resolved.available(),
                resolved.version(),
                resolved.error(),
                cfg.downloadUrl(),
                modelFor(cfg.id(), AiTask.ASSISTANT),
                modelFor(cfg.id(), AiTask.GENERATE_TOPIC)
        );
    }

    public boolean available(String providerId) {
        return status(providerId).available();
    }

    public String label(String providerId) {
        return config(providerId).label();
    }

    /**
     * Model name for display and generation provenance. Configuration may hold a
     * moving alias (Claude resolves {@code sonnet}/{@code opus} to the latest model
     * of that tier); once a run has reported which concrete id an alias resolved
     * to, that id is returned instead, so generated topics record the model that
     * actually produced them rather than the alias.
     */
    public String modelFor(String providerId, AiTask task) {
        ProviderConfig cfg = config(providerId);
        String configured = configuredModel(cfg, task);
        return resolvedModels.getOrDefault(modelKey(cfg.id(), configured), configured);
    }

    /** Model name passed to the CLI: always as configured, so aliases stay aliases. */
    private static String configuredModel(ProviderConfig cfg, AiTask task) {
        return task.needsStrongModel() ? cfg.generateModel() : cfg.assistantModel();
    }

    private static String modelKey(String providerId, String configuredModel) {
        return providerId + "|" + (configuredModel == null ? "" : configuredModel);
    }

    private void rememberResolvedModel(Invocation invocation, String reported) {
        if (reported == null || reported.isBlank()) {
            return;
        }
        resolvedModels.put(modelKey(invocation.config().id(), invocation.model()), reported.trim());
    }

    public SseEmitter stream(String providerId, String prompt, AiTask task) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MILLIS);
        executor.submit(() -> {
            try {
                Invocation invocation = invocation(config(providerId), task, prompt);
                runProcess(invocation, prompt, new EmitterSink(emitter));
            } catch (RuntimeException e) {
                sendStatus(emitter, "error", e.getMessage());
                emitter.complete();
            }
        });
        return emitter;
    }

    /** Receives normalized provider output decoupled from any single SSE client. */
    public interface Sink {
        void ai(String line);

        void status(String status, String message);
    }

    public void runDetached(String providerId, String prompt, AiTask task, Sink sink) {
        executor.submit(() -> {
            try {
                Invocation invocation = invocation(config(providerId), task, prompt);
                runProcess(invocation, prompt, sink);
            } catch (RuntimeException e) {
                sink.status("error", e.getMessage());
            }
        });
    }

    /**
     * Runs a provider to completion and returns collected assistant text. Used by
     * file-writing workflows where the important output is written to disk.
     */
    public String runForResult(String providerId, String prompt, AiTask task) {
        Invocation invocation = invocation(config(providerId), task, prompt);
        StringBuilder out = new StringBuilder();
        runProcess(invocation, prompt, new CollectingSink(out));
        return out.toString();
    }

    private ProviderConfig config(String providerId) {
        String id = providerId == null || providerId.isBlank()
                ? "claude"
                : providerId.toLowerCase(Locale.ROOT).trim();
        return providers.getOrDefault(id, providers.get("claude"));
    }

    private Invocation invocation(ProviderConfig cfg, AiTask task, String prompt) {
        ResolvedCommand resolved = resolve(cfg);
        if (!resolved.available()) {
            throw new RuntimeException(cfg.label() + " CLI is not available: " + resolved.error());
        }
        String model = configuredModel(cfg, task);
        return switch (cfg.kind()) {
            case CLAUDE -> claudeInvocation(cfg, resolved.command(), task, model);
            case CODEX -> codexInvocation(cfg, resolved.command(), task, model);
            case KIMI -> kimiInvocation(cfg, resolved.command(), task, model, prompt);
        };
    }

    private Invocation claudeInvocation(ProviderConfig cfg, String command, AiTask task, String model) {
        List<String> cmd = new ArrayList<>();
        cmd.add(command);
        cmd.add("-p");
        cmd.add("--output-format");
        cmd.add("stream-json");
        cmd.add("--verbose");
        cmd.add("--include-partial-messages");
        if (task.writesFiles()) {
            cmd.add("--permission-mode");
            cmd.add(cfg.claudePermissionMode());
        }
        if (model != null && !model.isBlank()) {
            cmd.add("--model");
            cmd.add(model);
        }
        return new Invocation(cfg, cmd, null, StreamFormat.CLAUDE_JSON, model);
    }

    private Invocation codexInvocation(ProviderConfig cfg, String command, AiTask task, String model) {
        List<String> cmd = new ArrayList<>();
        cmd.add(command);
        // Global flag: with exec this must appear before the subcommand.
        cmd.add("-a");
        cmd.add("never");
        cmd.add("exec");
        cmd.add("--json");
        cmd.add("--ephemeral");
        cmd.add("--sandbox");
        cmd.add(task.writesFiles() ? "workspace-write" : "read-only");
        if (model != null && !model.isBlank()) {
            cmd.add("--model");
            cmd.add(model);
        }
        String effort = task.needsStrongModel() ? cfg.codexGenerateEffort() : cfg.codexAssistantEffort();
        if (effort != null && !effort.isBlank()) {
            cmd.add("-c");
            cmd.add("model_reasoning_effort=\"" + effort + "\"");
        }
        cmd.add("-C");
        cmd.add(repoPaths.repoRoot().toString());
        cmd.add("-");
        return new Invocation(cfg, cmd, null, StreamFormat.CODEX_JSON, model);
    }

    private Invocation kimiInvocation(ProviderConfig cfg, String command, AiTask task, String model, String prompt) {
        try {
            String effectivePrompt = task.writesFiles()
                    ? prompt
                    : "Do not create, edit, delete, move, or overwrite any files. "
                    + "Do not run shell commands that modify the workspace. Answer only in text.\n\n"
                    + (prompt == null ? "" : prompt);
            Path promptFile = Files.createTempFile(repoPaths.repoRoot(), "ai-prompt-", ".txt");
            Files.writeString(promptFile, effectivePrompt == null ? "" : effectivePrompt, StandardCharsets.UTF_8);
            List<String> cmd = new ArrayList<>();
            cmd.add(command);
            cmd.add("--print");
            cmd.add("--output-format");
            cmd.add("stream-json");
            cmd.add("--work-dir");
            cmd.add(repoPaths.repoRoot().toString());
            cmd.add("--afk");
            if (task.needsStrongModel()) {
                cmd.add("--thinking");
            }
            if (model != null && !model.isBlank()) {
                cmd.add("--model");
                cmd.add(model);
            }
            cmd.add("--prompt");
            cmd.add("Read the full task instructions from this UTF-8 file and execute them exactly: "
                    + promptFile.toAbsolutePath());
            return new Invocation(cfg, cmd, promptFile, StreamFormat.KIMI_JSON, model);
        } catch (IOException e) {
            throw new RuntimeException("Could not write Kimi prompt file: " + e.getMessage(), e);
        }
    }

    private void runProcess(Invocation invocation, String prompt, Sink sink) {
        keepAwake.acquire();
        try {
            runProcessInner(invocation, prompt, sink);
        } finally {
            keepAwake.release();
        }
    }

    private void runProcessInner(Invocation invocation, String prompt, Sink sink) {
        ProviderConfig cfg = invocation.config();
        ProcessBuilder pb = new ProcessBuilder(invocation.command())
                .directory(repoPaths.repoRoot().toFile())
                .redirectErrorStream(false);
        // The legacy kimi-cli is Python: without UTF-8 mode it falls back to the
        // Windows ANSI codepage (cp1251) and mangles both the UTF-8 prompt file
        // it reads and its own stdout. Harmless for the non-Python providers.
        pb.environment().put("PYTHONUTF8", "1");
        Process process;
        try {
            sink.status("running", "Starting " + cfg.label() + "...");
            process = pb.start();
        } catch (IOException e) {
            cleanup(invocation);
            sink.status("error", "Could not start " + cfg.label() + " CLI: " + e.getMessage()
                    + " (command: " + invocation.command().get(0) + ")");
            return;
        }

        if (invocation.promptFile() == null) {
            try (OutputStream stdin = process.getOutputStream()) {
                stdin.write((prompt == null ? "" : prompt).getBytes(StandardCharsets.UTF_8));
                stdin.flush();
            } catch (IOException e) {
                log.warn("Failed writing prompt to {} stdin: {}", cfg.id(), e.getMessage());
            }
        }

        Thread stderrDrain = new Thread(() -> drainStderr(process, cfg.id()), cfg.id() + "-stderr");
        stderrDrain.setDaemon(true);
        stderrDrain.start();

        boolean timedOut = false;
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            Normalizer normalizer = new Normalizer(invocation);
            while ((line = br.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                for (String event : normalizer.apply(line)) {
                    sink.ai(event);
                }
            }
            if (!process.waitFor(RESULT_TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
                timedOut = true;
                process.destroyForcibly();
            }
            int exit = timedOut ? -1 : process.exitValue();
            sink.status(exit == 0 ? "done" : "error",
                    exit == 0 ? "Completed." : cfg.label() + (timedOut
                            ? " timed out."
                            : " exited with code " + exit + "."));
        } catch (IOException e) {
            sink.status("error", "Stream error: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            sink.status("error", cfg.label() + " run interrupted.");
        } finally {
            cleanup(invocation);
        }
    }

    /**
     * Per-stream normalizer. Stateful so Claude's final {@code assistant} message
     * text can be re-emitted as a {@code text_delta} fallback when the run produced
     * no incremental {@code content_block_delta} text. The frontend only
     * accumulates {@code text_delta} events, so without this fallback a reply that
     * arrived only as a single final message would be silently dropped.
     */
    private final class Normalizer {
        private final Invocation invocation;
        private boolean sawTextDelta = false;

        private Normalizer(Invocation invocation) {
            this.invocation = invocation;
        }

        List<String> apply(String line) {
            try {
                return switch (invocation.format()) {
                    case CLAUDE_JSON -> normalizeClaude(line);
                    case CODEX_JSON -> asList(normalizeCodex(line));
                    case KIMI_JSON -> asList(normalizeKimi(line));
                };
            } catch (RuntimeException | IOException e) {
                log.debug("Could not normalize AI stream line: {}", e.getMessage());
                // Legacy kimi-cli prints non-JSON trailer lines after the stream
                // (e.g. "To resume this session: ..."); don't leak them to the UI.
                if (invocation.format() == StreamFormat.KIMI_JSON) {
                    return List.of();
                }
                return asList(event("activity", line));
            }
        }

        private List<String> normalizeClaude(String line) throws IOException {
            JsonNode root = mapper.readTree(line);
            String type = root.path("type").asText("");
            if ("stream_event".equals(type)) {
                JsonNode evt = root.path("event");
                if ("content_block_delta".equals(evt.path("type").asText(""))) {
                    JsonNode delta = evt.path("delta");
                    if ("text_delta".equals(delta.path("type").asText(""))) {
                        sawTextDelta = true;
                        return asList(event("text_delta", delta.path("text").asText("")));
                    }
                }
            }
            if ("assistant".equals(type)) {
                rememberResolvedModel(invocation, root.path("message").path("model").asText(""));
                List<String> parts = new ArrayList<>();
                List<String> textParts = new ArrayList<>();
                for (JsonNode block : root.path("message").path("content")) {
                    if ("text".equals(block.path("type").asText("")) && !block.path("text").asText("").isBlank()) {
                        String text = block.path("text").asText("").trim();
                        parts.add(text);
                        textParts.add(text);
                    } else if ("tool_use".equals(block.path("type").asText(""))) {
                        String name = block.path("name").asText("tool");
                        JsonNode input = block.path("input");
                        String target = firstText(input, "file_path", "path", "command");
                        parts.add((name + " " + target).trim());
                    }
                }
                List<String> out = new ArrayList<>();
                // Fallback: if nothing streamed incrementally, deliver the final
                // assistant text so the frontend still receives the answer.
                if (!sawTextDelta && !textParts.isEmpty()) {
                    sawTextDelta = true;
                    event("text_delta", String.join("\n", textParts)).ifPresent(out::add);
                }
                if (!parts.isEmpty()) {
                    event("activity", String.join("\n", parts)).ifPresent(out::add);
                }
                return out;
            }
            if ("system".equals(type) && "init".equals(root.path("subtype").asText(""))) {
                rememberResolvedModel(invocation, root.path("model").asText(""));
                return asList(event("activity", "session started"));
            }
            if ("result".equals(type)) {
                return asList(event("activity", "result: " + root.path("subtype").asText("done")));
            }
            return List.of();
        }
    }

    private static List<String> asList(Optional<String> event) {
        return event.map(List::of).orElseGet(List::of);
    }

    private Optional<String> normalizeCodex(String line) throws IOException {
        JsonNode root = mapper.readTree(line);
        String type = root.path("type").asText("");
        if ("item.completed".equals(type) || "item.started".equals(type)) {
            JsonNode item = root.path("item");
            String itemType = item.path("type").asText("");
            if ("agent_message".equals(itemType)) {
                return event("text_delta", item.path("text").asText(""));
            }
            if ("command_execution".equals(itemType)) {
                return event("activity", "command: " + item.path("command").asText(""));
            }
            if ("file_change".equals(itemType) || "file_update".equals(itemType)) {
                return event("activity", "file: " + firstText(item, "path", "file_path"));
            }
            if (!itemType.isBlank()) {
                return event("activity", itemType);
            }
        }
        if ("thread.started".equals(type)) {
            return event("activity", "session started");
        }
        if ("turn.failed".equals(type) || "error".equals(type)) {
            return event("activity", "error: " + firstText(root, "message", "error"));
        }
        return Optional.empty();
    }

    private Optional<String> normalizeKimi(String line) throws IOException {
        JsonNode root = mapper.readTree(line);
        // Legacy kimi-cli v1.x emits Anthropic-style turns, one NDJSON line per
        // message: {"role":"assistant","content":[{"type":"think",...},
        // {"type":"text","text":"..."}]}. The reply text lives in the "text"
        // blocks of that array; tool calls appear as "tool_use" blocks.
        JsonNode content = root.path("content");
        if (content.isArray()) {
            // Only assistant turns carry the reply text. Other roles (user,
            // tool results) also arrive as content arrays — e.g. the CLI echoing
            // the prompt file it read — and must not leak into the chat.
            if (!"assistant".equals(root.path("role").asText("assistant"))) {
                return Optional.empty();
            }
            List<String> textParts = new ArrayList<>();
            List<String> activityParts = new ArrayList<>();
            for (JsonNode block : content) {
                String blockType = block.path("type").asText("");
                if ("text".equals(blockType) && !block.path("text").asText("").isBlank()) {
                    textParts.add(block.path("text").asText(""));
                } else if ("tool_use".equals(blockType)) {
                    String name = block.path("name").asText("tool");
                    String target = firstText(block.path("input"), "file_path", "path", "command");
                    activityParts.add((name + " " + target).trim());
                }
            }
            if (!textParts.isEmpty()) {
                return event("text_delta", String.join("\n", textParts));
            }
            if (!activityParts.isEmpty()) {
                return event("activity", String.join("\n", activityParts));
            }
            return Optional.empty();
        }
        String text = firstText(root, "delta", "text", "content", "message");
        if (!text.isBlank()) {
            String type = root.path("type").asText("").toLowerCase(Locale.ROOT);
            boolean finalOnly = type.contains("assistant") || type.contains("message") || type.contains("delta");
            return event(finalOnly ? "text_delta" : "activity", text);
        }
        String type = root.path("type").asText("");
        return type.isBlank() ? Optional.empty() : event("activity", type);
    }

    private Optional<String> event(String type, String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(mapper.writeValueAsString(Map.of("type", type, "text", text)));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private static String firstText(JsonNode node, String... fields) {
        for (String f : fields) {
            JsonNode v = node.path(f);
            if (v.isTextual() && !v.asText().isBlank()) {
                return v.asText();
            }
        }
        return "";
    }

    private void drainStderr(Process process, String providerId) {
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                log.debug("[{} stderr] {}", providerId, line);
            }
        } catch (IOException ignored) {
        }
    }

    private void cleanup(Invocation invocation) {
        if (invocation != null && invocation.promptFile() != null) {
            try {
                Files.deleteIfExists(invocation.promptFile());
            } catch (IOException ignored) {
            }
        }
    }

    private ResolvedCommand resolve(ProviderConfig cfg) {
        synchronized (resolvedCache) {
            ResolvedCommand cached = resolvedCache.get(cfg.id());
            if (cached != null) {
                return cached;
            }
        }
        ResolvedCommand resolved = resolveFresh(cfg);
        synchronized (resolvedCache) {
            resolvedCache.put(cfg.id(), resolved);
        }
        return resolved;
    }

    private ResolvedCommand resolveFresh(ProviderConfig cfg) {
        List<String> errors = new ArrayList<>();
        for (String candidate : candidates(cfg)) {
            if (candidate == null || candidate.isBlank()) {
                continue;
            }
            VersionCheck check = checkVersion(candidate.trim());
            if (check.ok()) {
                return new ResolvedCommand(true, candidate.trim(), check.version(), null);
            }
            errors.add(candidate.trim() + ": " + check.error());
        }
        String msg = errors.isEmpty() ? "command not found" : String.join("; ", errors);
        return new ResolvedCommand(false, "", "", msg);
    }

    private List<String> candidates(ProviderConfig cfg) {
        Set<String> out = new LinkedHashSet<>();
        if (cfg.command() != null && !cfg.command().isBlank()) {
            out.add(cfg.command());
        }
        switch (cfg.kind()) {
            case CLAUDE -> out.add("claude");
            case CODEX -> out.add("codex");
            case KIMI -> {
                out.add("kimi");
                out.add("kimi.cmd");
                out.add("kimi.exe");
            }
        }
        return new ArrayList<>(out);
    }

    private VersionCheck checkVersion(String command) {
        ProcessBuilder pb = new ProcessBuilder(command, "--version")
                .directory(repoPaths.repoRoot().toFile())
                .redirectErrorStream(true);
        try {
            Process process = pb.start();
            String output;
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                output = br.lines().findFirst().orElse("").trim();
            }
            boolean done = process.waitFor(Duration.ofSeconds(5).toMillis(), TimeUnit.MILLISECONDS);
            if (!done) {
                process.destroyForcibly();
                return new VersionCheck(false, "", "version check timed out");
            }
            if (process.exitValue() == 0) {
                return new VersionCheck(true, output, null);
            }
            return new VersionCheck(false, "", output.isBlank() ? "exit " + process.exitValue() : output);
        } catch (IOException e) {
            return new VersionCheck(false, "", e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new VersionCheck(false, "", "interrupted");
        }
    }

    private void sendStatus(SseEmitter emitter, String status, String message) {
        try {
            emitter.send(SseEmitter.event().name("status").data(statusJson(status, message)));
        } catch (IOException ignored) {
        }
    }

    private String statusJson(String status, String message) {
        try {
            return mapper.writeValueAsString(Map.of("status", status, "message", message));
        } catch (IOException e) {
            return "{\"status\":\"" + status + "\",\"message\":\"" + message.replace("\"", "'") + "\"}";
        }
    }

    private enum ProviderKind {
        CLAUDE,
        CODEX,
        KIMI
    }

    private enum StreamFormat {
        CLAUDE_JSON,
        CODEX_JSON,
        KIMI_JSON
    }

    private record ProviderConfig(
            String id,
            String label,
            ProviderKind kind,
            String command,
            String assistantModel,
            String generateModel,
            String downloadUrl,
            String claudePermissionMode,
            String codexAssistantEffort,
            String codexGenerateEffort
    ) {
    }

    private record ResolvedCommand(boolean available, String command, String version, String error) {
    }

    private record VersionCheck(boolean ok, String version, String error) {
    }

    private record Invocation(
            ProviderConfig config,
            List<String> command,
            Path promptFile,
            StreamFormat format,
            // Model name as configured, i.e. the key an alias resolution is cached under.
            String model
    ) {
    }

    private final class EmitterSink implements Sink {
        private final SseEmitter emitter;

        private EmitterSink(SseEmitter emitter) {
            this.emitter = emitter;
        }

        @Override
        public void ai(String line) {
            try {
                emitter.send(SseEmitter.event().name("ai").data(line));
            } catch (IOException e) {
                throw new RuntimeException("SSE client disconnected", e);
            }
        }

        @Override
        public void status(String status, String message) {
            sendStatus(emitter, status, message);
            if ("done".equals(status) || "error".equals(status)) {
                emitter.complete();
            }
        }
    }

    private static final class CollectingSink implements Sink {
        private final StringBuilder out;

        private CollectingSink(StringBuilder out) {
            this.out = out;
        }

        @Override
        public void ai(String line) {
            try {
                JsonNode n = new ObjectMapper().readTree(line);
                if ("text_delta".equals(n.path("type").asText(""))) {
                    out.append(n.path("text").asText(""));
                }
            } catch (IOException ignored) {
            }
        }

        @Override
        public void status(String status, String message) {
            if ("error".equals(status)) {
                throw new RuntimeException(message);
            }
        }
    }
}
