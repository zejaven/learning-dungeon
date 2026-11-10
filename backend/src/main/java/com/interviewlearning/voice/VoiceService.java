package com.interviewlearning.voice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewlearning.config.RepoPaths;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Speech-to-text for the Boss Fight voice input. Drives the long-lived python
 * worker {@code tools/voice/transcribe_worker.py} (faster-whisper) over
 * stdin/stdout, one JSON line per request, so nothing listens on a port.
 *
 * <p>The worker is started on the first request and exits by itself after a
 * spell of idleness to give the GPU back (the zeveon-transcriber bot shares
 * it); the next request simply starts it again. Requests are serialized: one
 * GPU, one model, one answer being dictated at a time.
 *
 * <p>The model follows the UI language: Russian speech goes to a Russian
 * fine-tune, English to large-v3-turbo.
 */
@Service
public class VoiceService {

    private static final Logger log = LoggerFactory.getLogger(VoiceService.class);

    /** Whisper reads at most ~224 prompt tokens; the question is the useful part. */
    static final int PROMPT_MAX_CHARS = 400;
    private static final long REQUEST_TIMEOUT_SECONDS = 120;
    private static final String WORKER = "tools/voice/transcribe_worker.py";

    private final ObjectMapper mapper;
    private final Path python;
    private final Path worker;
    private final String modelRu;
    private final String modelEn;
    private final String device;
    private final String computeType;
    private final double idleMinutes;
    private final AtomicLong ids = new AtomicLong();

    private Process process;
    private BufferedWriter stdin;
    private BufferedReader stdout;

    public VoiceService(RepoPaths repoPaths,
                        ObjectMapper mapper,
                        @Value("${app.voice.python:}") String python,
                        @Value("${app.voice.model-ru:bzikst/faster-whisper-large-v3-russian}") String modelRu,
                        @Value("${app.voice.model-en:large-v3-turbo}") String modelEn,
                        @Value("${app.voice.device:cuda}") String device,
                        @Value("${app.voice.compute-type:int8_float16}") String computeType,
                        @Value("${app.voice.idle-minutes:10}") double idleMinutes) {
        this.mapper = mapper;
        this.python = resolvePython(python, repoPaths.repoRoot());
        this.worker = repoPaths.repoRoot().resolve(WORKER);
        this.modelRu = modelRu;
        this.modelEn = modelEn;
        this.device = device;
        this.computeType = computeType;
        this.idleMinutes = idleMinutes;
    }

    /** Whether voice input can work here, and why not. */
    public record Status(boolean available, String reason) {
    }

    public Status status() {
        if (python == null) {
            return new Status(false, "No python with faster-whisper: set app.voice.python in config/secret.yml.");
        }
        if (!Files.isRegularFile(python)) {
            return new Status(false, "app.voice.python points to a missing file: " + python);
        }
        if (!Files.isRegularFile(worker)) {
            return new Status(false, "Missing " + WORKER);
        }
        return new Status(true, "");
    }

    /** Loads the model for {@code lang} ahead of the transcription, while the user is still speaking. */
    public void warmup(String lang) throws IOException {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("cmd", "warmup");
        request.put("lang", languageOf(lang));
        call(request);
    }

    /** The text spoken in {@code audio} (any format ffmpeg reads). */
    public String transcribe(Path audio, String lang, String question) throws IOException {
        String language = languageOf(lang);
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("audio", audio.toAbsolutePath().toString());
        request.put("lang", language);
        request.put("prompt", promptFor(language, question));
        JsonNode reply = call(request);
        return reply.path("text").asText("");
    }

    /** The UI languages map onto the two models; anything else is treated as English. */
    static String languageOf(String lang) {
        return "ru".equalsIgnoreCase(lang == null ? "" : lang.trim()) ? "ru" : "en";
    }

    /**
     * Whisper continues the style of its prompt. A Russian prompt that spells
     * terms in Latin keeps "HashMap", "get", "null" in Latin instead of
     * "Хешмэп", "джет", "нол". The examples matter: without them a question
     * that holds no Latin terms (a QA one) still yields Cyrillic spellings.
     * The question itself brings the topic's own terms.
     */
    static String promptFor(String language, String question) {
        String lead = "ru".equals(language)
                ? "Ответ на вопрос собеседования. Технические термины пишутся по-английски, "
                        + "например: HashMap, null, get(), equals(), API, REST."
                : "An answer to a technical interview question.";
        String q = question == null ? "" : question.replaceAll("\\s+", " ").trim();
        String prompt = q.isEmpty() ? lead : lead + " " + q;
        return prompt.length() <= PROMPT_MAX_CHARS ? prompt : prompt.substring(0, PROMPT_MAX_CHARS);
    }

    /** Throws the worker's own error message, so the UI can say what went wrong. */
    static JsonNode checkReply(JsonNode reply) throws IOException {
        if (reply.hasNonNull("error")) {
            throw new IOException("Transcription failed: " + reply.get("error").asText());
        }
        return reply;
    }

    private synchronized JsonNode call(Map<String, Object> request) throws IOException {
        Status status = status();
        if (!status.available()) {
            throw new IllegalStateException(status.reason());
        }
        // The worker exits on its own when idle; a dead one is replaced, once.
        for (int attempt = 0; ; attempt++) {
            ensureRunning();
            String id = Long.toString(ids.incrementAndGet());
            request.put("id", id);
            try {
                try {
                    stdin.write(mapper.writeValueAsString(request));
                    stdin.newLine();
                    stdin.flush();
                } catch (IOException e) {
                    throw new WorkerGone("cannot write: " + e.getMessage()); // it exited while idle
                }
                return checkReply(readReply(id));
            } catch (WorkerGone e) {
                stop();
                if (attempt > 0) {
                    throw new IOException("The voice worker stopped: " + e.getMessage());
                }
                log.info("Voice worker was gone ({}); starting it again", e.getMessage());
            }
        }
    }

    /** The reply line for {@code id}, within the request timeout. */
    private JsonNode readReply(String id) throws IOException {
        CompletableFuture<String> line = CompletableFuture.supplyAsync(() -> {
            try {
                return stdout.readLine();
            } catch (IOException e) {
                return null;
            }
        });
        String text;
        try {
            text = line.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            stop(); // a stuck worker would block every later request
            throw new IOException("Transcription timed out after " + REQUEST_TIMEOUT_SECONDS + "s");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted");
        } catch (ExecutionException e) {
            throw new WorkerGone(String.valueOf(e.getCause()));
        }
        if (text == null) {
            throw new WorkerGone("end of output");
        }
        JsonNode reply = mapper.readTree(text);
        if (!id.equals(reply.path("id").asText())) {
            throw new IOException("Voice worker answered out of turn: " + text);
        }
        return reply;
    }

    private void ensureRunning() throws IOException {
        if (process != null && process.isAlive()) {
            return;
        }
        List<String> cmd = new ArrayList<>(List.of(
                python.toString(), worker.toString(),
                "--model-ru", modelRu,
                "--model-en", modelEn,
                "--device", device,
                "--compute-type", computeType,
                "--idle-minutes", Double.toString(idleMinutes)));
        ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(false);
        pb.environment().put("PYTHONUTF8", "1");
        // hf_xet stalls on some downloads; the plain HTTP path is reliable (see zeveon-transcriber).
        pb.environment().put("HF_HUB_DISABLE_XET", "1");
        process = pb.start();
        stdin = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        stdout = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
        Process started = process;
        Thread drain = new Thread(() -> drainStderr(started), "voice-stderr");
        drain.setDaemon(true);
        drain.start();
        log.info("Started the voice worker ({})", python);
    }

    private static void drainStderr(Process p) {
        try (BufferedReader err = new BufferedReader(
                new InputStreamReader(p.getErrorStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = err.readLine()) != null) {
                if (!line.isBlank()) {
                    log.info(line);
                }
            }
        } catch (IOException ignored) {
            // the worker exited
        }
    }

    @PreDestroy
    synchronized void stop() {
        if (process != null) {
            process.destroyForcibly();
            process = null;
        }
    }

    /** Explicit python, else the zeveon-transcriber venv next to this repo if it exists. */
    static Path resolvePython(String configured, Path repoRoot) {
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured.trim());
        }
        Path sibling = repoRoot.toAbsolutePath().normalize().getParent();
        if (sibling == null) {
            return null;
        }
        Path venv = sibling.resolve("zeveon-transcriber").resolve(".venv");
        Path windows = venv.resolve("Scripts").resolve("python.exe");
        Path unix = venv.resolve("bin").resolve("python");
        return Files.isRegularFile(windows) ? windows : Files.isRegularFile(unix) ? unix : null;
    }

    /** The worker process died or closed its output: worth one restart. */
    private static final class WorkerGone extends IOException {
        WorkerGone(String message) {
            super(message);
        }
    }
}
