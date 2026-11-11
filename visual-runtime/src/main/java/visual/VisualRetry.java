package visual;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

/**
 * A deterministic teaching model of a client that retries a failed remote call.
 * It shows exponential backoff, jitter, which failures are worth retrying,
 * why ambiguous failures need idempotency, how a deadline and a retry budget
 * stop retries, how synchronized clients form a thundering herd, and how
 * retrying at every layer of a call stack multiplies the load.
 *
 * <p>The server is scripted: {@link #script(String...)} lists the outcome of
 * each successive attempt ({@code "200"}, {@code "503"}, {@code "TIMEOUT"},
 * {@code "CONNECT_REFUSED"}, {@code "429:800"} = 429 with Retry-After 800 ms,
 * any other HTTP status). When the script runs out, its last outcome repeats.
 * Jitter uses a seeded {@link Random}, so every run prints the same trace.
 */
public class VisualRetry {

    private static final int HISTORY_LIMIT = 10;
    private static final int FAST_ATTEMPT_MS = 20;

    private final String name;
    private final List<Map<String, Object>> history = new ArrayList<>();
    private final List<Map<String, Object>> attempts = new ArrayList<>();
    private final List<Map<String, Object>> slots = new ArrayList<>();
    private final List<Map<String, Object>> layers = new ArrayList<>();

    private int maxAttempts = 3;
    private int baseDelayMs = 100;
    private double multiplier = 2.0;
    private int maxDelayMs = 2_000;
    private String jitter = "NONE";
    private int attemptTimeoutMs = 200;
    private int deadlineMs;
    private int budgetMax = -1;
    private int budgetTokens = -1;
    private Random random = new Random(42);

    private List<String> script = List.of("200");
    private String operation = "";
    private boolean idempotent;
    private String idempotencyKey = "";
    private String status = "IDLE";
    private int clockMs;
    private int serverCalls;
    private int peak;

    public VisualRetry() {
        this("client");
    }

    public VisualRetry(String name) {
        this.name = Objects.requireNonNull(name, "name");
        addHistory("client", "CREATE", name);
        Trace.event("RETRY_SCENE_CREATED",
                "Created retry scene '" + name + "'",
                "Создана сцена retry '" + name + "'",
                List.of("client"),
                state());
    }

    // ---------------------------------------------------------------- policy

    /** Total attempts including the first one (3 = one call + two retries). */
    public VisualRetry maxAttempts(int maxAttempts) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be >= 1");
        }
        this.maxAttempts = maxAttempts;
        return policyChanged();
    }

    /** delay(retry k) = min(maxDelayMs, baseDelayMs * multiplier^k), k from 0. */
    public VisualRetry backoff(int baseDelayMs, double multiplier, int maxDelayMs) {
        if (baseDelayMs < 0 || multiplier < 1.0 || maxDelayMs < baseDelayMs) {
            throw new IllegalArgumentException("invalid backoff");
        }
        this.baseDelayMs = baseDelayMs;
        this.multiplier = multiplier;
        this.maxDelayMs = maxDelayMs;
        return policyChanged();
    }

    /** NONE, FULL (random in [0, d]) or EQUAL (d/2 + random in [0, d/2]). */
    public VisualRetry jitter(String mode) {
        String m = Objects.requireNonNull(mode, "mode").toUpperCase();
        if (!m.equals("NONE") && !m.equals("FULL") && !m.equals("EQUAL")) {
            throw new IllegalArgumentException("jitter must be NONE, FULL or EQUAL");
        }
        this.jitter = m;
        this.random = new Random(42);
        return policyChanged();
    }

    /** How long a TIMEOUT attempt costs the caller. */
    public VisualRetry attemptTimeout(int ms) {
        if (ms <= 0) {
            throw new IllegalArgumentException("attemptTimeout must be positive");
        }
        this.attemptTimeoutMs = ms;
        return policyChanged();
    }

    /** End-to-end deadline of one call, retries included. 0 = none. */
    public VisualRetry deadline(int ms) {
        if (ms < 0) {
            throw new IllegalArgumentException("deadline must not be negative");
        }
        this.deadlineMs = ms;
        return policyChanged();
    }

    /** Retry tokens shared by every call of this client; each retry spends one. */
    public VisualRetry retryBudget(int tokens) {
        if (tokens < 0) {
            throw new IllegalArgumentException("budget must not be negative");
        }
        this.budgetMax = tokens;
        this.budgetTokens = tokens;
        return policyChanged();
    }

    private VisualRetry policyChanged() {
        addHistory("client", "POLICY", policyText());
        Trace.event("RETRY_POLICY_SET",
                "Retry policy: " + policyText(),
                "Политика retry: " + policyText(),
                List.of("policy"),
                state());
        return this;
    }

    // ---------------------------------------------------------------- server script

    public VisualRetry script(String... outcomes) {
        if (outcomes == null || outcomes.length == 0) {
            throw new IllegalArgumentException("script must not be empty");
        }
        for (String o : outcomes) {
            parse(o);
        }
        this.script = List.copyOf(Arrays.asList(outcomes));
        addHistory("server", "SCRIPT", String.join(",", outcomes));
        Trace.event("SERVER_SCRIPTED",
                "The server will answer: " + String.join(", ", outcomes)
                        + " (the last outcome repeats)",
                "Сервер будет отвечать: " + String.join(", ", outcomes)
                        + " (последний ответ повторяется)",
                List.of("server"),
                state());
        return this;
    }

    // ---------------------------------------------------------------- one call with retries

    /** Calls without an idempotency key. GET/PUT/DELETE/HEAD are idempotent; POST/PATCH are not. */
    public boolean call(String operation) {
        return run(operation, "");
    }

    /** Calls with an idempotency key, which makes a POST safe to repeat. */
    public boolean callWithKey(String operation, String key) {
        return run(operation, Objects.requireNonNull(key, "key"));
    }

    private boolean run(String op, String key) {
        this.operation = Objects.requireNonNull(op, "operation");
        this.idempotencyKey = key;
        this.idempotent = isIdempotentMethod(op) || !key.isEmpty();
        this.attempts.clear();
        this.slots.clear();
        this.layers.clear();
        this.clockMs = 0;
        this.serverCalls = 0;
        this.peak = 0;
        this.status = "CALLING";

        for (int n = 1; ; n++) {
            Outcome outcome = parse(script.get(Math.min(n - 1, script.size() - 1)));
            Map<String, Object> attempt = new LinkedHashMap<>();
            attempt.put("n", n);
            attempt.put("atMs", clockMs);
            attempt.put("outcome", outcome.label);
            attempt.put("verdict", "PENDING");
            attempt.put("delayMs", 0);
            attempts.add(attempt);
            status = "CALLING";
            serverCalls++;
            addHistory("client", "ATTEMPT_" + n, op);
            Trace.event("ATTEMPT_STARTED",
                    "Attempt " + n + ": " + op + " at t=" + clockMs + " ms",
                    "Попытка " + n + ": " + op + " в t=" + clockMs + " ms",
                    List.of("client", "attempt:" + n),
                    state());

            clockMs += outcome.kind.equals("TIMEOUT") ? attemptTimeoutMs : FAST_ATTEMPT_MS;
            String cls = classify(outcome);

            if (cls.equals("SUCCESS")) {
                attempt.put("verdict", "OK");
                status = "SUCCEEDED";
                addHistory("server", "OK", outcome.label);
                Trace.event("CALL_SUCCEEDED",
                        "Attempt " + n + " got " + outcome.label + "; the call succeeds after "
                                + (n - 1) + " retr" + (n - 1 == 1 ? "y" : "ies") + " at t=" + clockMs + " ms",
                        "Попытка " + n + " получила " + outcome.label + "; вызов успешен после "
                                + (n - 1) + " retry, t=" + clockMs + " ms",
                        List.of("client", "attempt:" + n),
                        state());
                return true;
            }

            attempt.put("verdict", cls);
            addHistory("server", "FAIL", outcome.label);
            Trace.event("ATTEMPT_FAILED",
                    "Attempt " + n + " failed with " + outcome.label + " → " + verdictEn(cls),
                    "Попытка " + n + " упала с " + outcome.label + " → " + verdictRu(cls),
                    List.of("attempt:" + n),
                    state());

            if (cls.equals("PERMANENT")) {
                return giveUp("RETRY_SKIPPED_NON_RETRYABLE",
                        outcome.label + " is a permanent error: the same request will fail the same way, so it is not retried",
                        outcome.label + " — постоянная ошибка: тот же запрос упадёт так же, поэтому retry не делаем",
                        n);
            }
            if (cls.equals("AMBIGUOUS") && !idempotent) {
                return giveUp("RETRY_SKIPPED_NOT_IDEMPOTENT",
                        outcome.label + " is ambiguous: the server may already have done the work. "
                                + op + " is not idempotent and has no idempotency key, so a retry could duplicate it",
                        outcome.label + " — неоднозначный результат: сервер мог уже выполнить работу. "
                                + op + " не идемпотентен и без idempotency key, повтор может создать дубль",
                        n);
            }
            if (n >= maxAttempts) {
                return giveUp("ATTEMPTS_EXHAUSTED",
                        "All " + maxAttempts + " attempts failed; the error goes to the caller",
                        "Все " + maxAttempts + " попытки исчерпаны; ошибка уходит вызывающему",
                        n);
            }
            if (budgetTokens == 0) {
                return giveUp("RETRY_BUDGET_EXHAUSTED",
                        "The retry budget is empty: this client already retries too much, so it fails fast instead of adding load",
                        "Retry budget пуст: клиент уже слишком много повторяет, поэтому быстро отказывает вместо добавления нагрузки",
                        n);
            }

            int backoff = jittered(rawDelay(n - 1));
            int delay = backoff;
            if (outcome.retryAfterMs > 0 && outcome.retryAfterMs > backoff) {
                delay = outcome.retryAfterMs;
            }
            if (deadlineMs > 0 && clockMs + delay >= deadlineMs) {
                return giveUp("DEADLINE_EXCEEDED",
                        "Waiting " + delay + " ms more would cross the " + deadlineMs
                                + " ms deadline (now t=" + clockMs + "), so the client stops retrying",
                        "Ожидание ещё " + delay + " ms пересечёт deadline " + deadlineMs
                                + " ms (сейчас t=" + clockMs + "), поэтому клиент прекращает retry",
                        n);
            }
            if (budgetTokens > 0) {
                budgetTokens--;
            }
            attempt.put("delayMs", delay);
            status = "BACKING_OFF";
            clockMs += delay;
            if (outcome.retryAfterMs > 0 && delay == outcome.retryAfterMs) {
                addHistory("client", "RETRY_AFTER", delay + " ms");
                Trace.event("RETRY_AFTER_HONORED",
                        "The server asked to come back in " + outcome.retryAfterMs
                                + " ms (Retry-After), longer than the backoff " + backoff + " ms, so the client waits " + delay + " ms",
                        "Сервер попросил вернуться через " + outcome.retryAfterMs
                                + " ms (Retry-After) — дольше backoff " + backoff + " ms, клиент ждёт " + delay + " ms",
                        List.of("attempt:" + n, "server"),
                        state());
            } else {
                addHistory("client", "BACKOFF", delay + " ms");
                Trace.event("BACKOFF_SCHEDULED",
                        "Backoff before attempt " + (n + 1) + ": " + rawDelay(n - 1) + " ms"
                                + (jitter.equals("NONE") ? "" : " → " + delay + " ms with " + jitter + " jitter"),
                        "Пауза перед попыткой " + (n + 1) + ": " + rawDelay(n - 1) + " ms"
                                + (jitter.equals("NONE") ? "" : " → " + delay + " ms с " + jitter + " jitter"),
                        List.of("attempt:" + n, "policy"),
                        state());
            }
        }
    }

    private boolean giveUp(String event, String en, String ru, int n) {
        status = "FAILED";
        addHistory("client", event, operation);
        Trace.event(event, en, ru, List.of("client", "attempt:" + n), state());
        return false;
    }

    // ---------------------------------------------------------------- thundering herd

    /**
     * {@code clients} clients all fail at t=0 (a short server blip) and retry
     * with the current policy. Counts how many retries land in each
     * {@code slotMs} window: without jitter they arrive together.
     */
    public int herd(int clients, int slotMs) {
        if (clients < 1 || slotMs < 1) {
            throw new IllegalArgumentException("clients and slotMs must be positive");
        }
        attempts.clear();
        layers.clear();
        slots.clear();
        operation = clients + " clients";
        status = "CALLING";
        clockMs = 0;

        Map<Integer, Integer> counts = new java.util.TreeMap<>();
        int retries = maxAttempts - 1;
        for (int c = 0; c < clients; c++) {
            int t = 0;
            for (int k = 0; k < retries; k++) {
                t += FAST_ATTEMPT_MS + jittered(rawDelay(k));
                counts.merge(t / slotMs, 1, Integer::sum);
            }
        }
        int last = counts.isEmpty() ? 0 : ((java.util.TreeMap<Integer, Integer>) counts).lastKey();
        peak = 0;
        for (int i = 0; i <= last; i++) {
            int count = counts.getOrDefault(i, 0);
            Map<String, Object> slot = new LinkedHashMap<>();
            slot.put("fromMs", i * slotMs);
            slot.put("count", count);
            slots.add(slot);
            peak = Math.max(peak, count);
        }
        serverCalls = clients * retries;
        clockMs = (last + 1) * slotMs;
        status = "DONE";
        boolean synced = peak >= clients;
        addHistory("server", synced ? "HERD_SYNC" : "HERD_SPREAD", "peak " + peak);
        if (synced) {
            Trace.event("HERD_RETRIES_SYNCHRONIZED",
                    clients + " clients retried with " + jitter + " jitter: all of them hit the server in the same "
                            + slotMs + " ms window (peak " + peak + "), a thundering herd",
                    clients + " клиентов с " + jitter + " jitter: все ударили в сервер в одном окне "
                            + slotMs + " ms (пик " + peak + ") — thundering herd",
                    List.of("server"),
                    state());
        } else {
            Trace.event("HERD_RETRIES_SPREAD",
                    clients + " clients retried with " + jitter + " jitter: retries spread out, the busiest "
                            + slotMs + " ms window got only " + peak,
                    clients + " клиентов с " + jitter + " jitter: повторы размазаны, в самом загруженном окне "
                            + slotMs + " ms всего " + peak,
                    List.of("server"),
                    state());
        }
        return peak;
    }

    // ---------------------------------------------------------------- retry amplification

    /**
     * A request passes through {@code stack} (outermost first) to a database
     * that is down. Every layer listed in {@code retrying} makes up to
     * maxAttempts attempts per call it receives; the others make one.
     * Returns how many calls hit the last layer.
     */
    public int amplify(List<String> stack, List<String> retrying) {
        if (stack == null || stack.size() < 2) {
            throw new IllegalArgumentException("stack needs at least two layers");
        }
        attempts.clear();
        slots.clear();
        layers.clear();
        operation = String.join(" → ", stack);
        status = "CALLING";
        clockMs = 0;
        peak = 0;

        int calls = 1;
        for (int i = 0; i < stack.size(); i++) {
            String layer = stack.get(i);
            boolean retries = i < stack.size() - 1 && retrying.contains(layer);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", layer);
            item.put("retries", retries);
            item.put("calls", calls);
            layers.add(item);
            serverCalls = calls;
            addHistory(layer, "RECEIVES", calls + " calls");
            Trace.event("LAYER_CALLED",
                    "'" + layer + "' receives " + calls + " call" + (calls == 1 ? "" : "s")
                            + (retries ? " and retries each up to " + maxAttempts + " times" : ""),
                    "'" + layer + "' получает " + calls + " вызов(ов)"
                            + (retries ? " и повторяет каждый до " + maxAttempts + " раз" : ""),
                    List.of("layer:" + layer),
                    state());
            if (i < stack.size() - 1) {
                calls *= retries ? maxAttempts : 1;
            }
        }
        status = "FAILED";
        String last = stack.get(stack.size() - 1);
        if (serverCalls > maxAttempts) {
            Trace.event("RETRY_AMPLIFIED",
                    "One user request became " + serverCalls + " calls to '" + last
                            + "': retries at several layers multiply (" + maxAttempts + "^" + retrying.size() + ")",
                    "Один запрос пользователя превратился в " + serverCalls + " вызовов '" + last
                            + "': retry на нескольких уровнях перемножаются (" + maxAttempts + "^" + retrying.size() + ")",
                    List.of("layer:" + last),
                    state());
        } else {
            Trace.event("RETRY_CONTAINED",
                    "Only one layer retries, so '" + last + "' sees at most " + serverCalls + " calls per user request",
                    "Повторяет только один уровень, поэтому '" + last + "' получает не больше " + serverCalls
                            + " вызовов на запрос пользователя",
                    List.of("layer:" + last),
                    state());
        }
        return serverCalls;
    }

    // ---------------------------------------------------------------- helpers

    private int rawDelay(int retryIndex) {
        double d = baseDelayMs * Math.pow(multiplier, retryIndex);
        return (int) Math.min(maxDelayMs, Math.round(d));
    }

    private int jittered(int d) {
        switch (jitter) {
            case "FULL":
                return random.nextInt(d + 1);
            case "EQUAL":
                return d / 2 + random.nextInt(d / 2 + 1);
            default:
                return d;
        }
    }

    private static boolean isIdempotentMethod(String op) {
        String method = op.trim().split("\\s+")[0].toUpperCase();
        return method.equals("GET") || method.equals("PUT") || method.equals("DELETE")
                || method.equals("HEAD") || method.equals("OPTIONS");
    }

    private static String classify(Outcome o) {
        switch (o.kind) {
            case "TIMEOUT":
                return "AMBIGUOUS";
            case "CONNECT_REFUSED":
                return "TRANSIENT";
            default:
                int code = Integer.parseInt(o.kind);
                if (code < 400) {
                    return "SUCCESS";
                }
                if (code == 429 || code == 503) {
                    return "TRANSIENT";
                }
                if (code == 408 || code == 500 || code == 502 || code == 504) {
                    return "AMBIGUOUS";
                }
                return "PERMANENT";
        }
    }

    private static String verdictEn(String cls) {
        switch (cls) {
            case "TRANSIENT":
                return "transient, the request was not processed: safe to retry";
            case "AMBIGUOUS":
                return "ambiguous, the server may have processed it: retry only if idempotent";
            default:
                return "permanent: do not retry";
        }
    }

    private static String verdictRu(String cls) {
        switch (cls) {
            case "TRANSIENT":
                return "временная, запрос не обработан: повтор безопасен";
            case "AMBIGUOUS":
                return "неоднозначная, сервер мог его обработать: повтор только для идемпотентного";
            default:
                return "постоянная: не повторять";
        }
    }

    private static Outcome parse(String raw) {
        String s = Objects.requireNonNull(raw, "outcome").trim().toUpperCase();
        if (s.equals("TIMEOUT") || s.equals("CONNECT_REFUSED")) {
            return new Outcome(s, s, 0);
        }
        String[] parts = s.split(":");
        int code;
        try {
            code = Integer.parseInt(parts[0]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("unknown outcome: " + raw);
        }
        if (code < 100 || code > 599) {
            throw new IllegalArgumentException("unknown status: " + raw);
        }
        int retryAfter = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
        String label = parts.length > 1 ? code + " Retry-After " + retryAfter + "ms" : String.valueOf(code);
        return new Outcome(parts[0], label, retryAfter);
    }

    private String policyText() {
        String text = maxAttempts + " attempts, backoff " + baseDelayMs + "ms x" + trim(multiplier)
                + " cap " + maxDelayMs + "ms, jitter " + jitter;
        if (deadlineMs > 0) {
            text += ", deadline " + deadlineMs + "ms";
        }
        if (budgetMax >= 0) {
            text += ", budget " + budgetMax;
        }
        return text;
    }

    private static String trim(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
    }

    private synchronized void addHistory(String actor, String action, String target) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("actor", actor);
        item.put("action", action);
        item.put("target", target);
        history.add(item);
        if (history.size() > HISTORY_LIMIT) {
            history.remove(0);
        }
    }

    private synchronized Object state() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("name", name);

        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("maxAttempts", maxAttempts);
        policy.put("baseDelayMs", baseDelayMs);
        policy.put("multiplier", trim(multiplier));
        policy.put("maxDelayMs", maxDelayMs);
        policy.put("jitter", jitter);
        policy.put("attemptTimeoutMs", attemptTimeoutMs);
        policy.put("deadlineMs", deadlineMs);
        policy.put("budgetMax", budgetMax);
        policy.put("budgetTokens", budgetTokens);
        s.put("policy", policy);

        s.put("operation", operation);
        s.put("idempotent", idempotent);
        s.put("idempotencyKey", idempotencyKey);
        s.put("status", status);
        s.put("clockMs", clockMs);
        s.put("serverCalls", serverCalls);
        s.put("peak", peak);
        s.put("attempts", copy(attempts));
        s.put("slots", copy(slots));
        s.put("layers", copy(layers));
        s.put("history", copy(history));
        return s;
    }

    private static List<Object> copy(List<Map<String, Object>> list) {
        List<Object> out = new ArrayList<>();
        for (Map<String, Object> m : list) {
            out.add(new LinkedHashMap<>(m));
        }
        return out;
    }

    private static final class Outcome {
        final String kind;
        final String label;
        final int retryAfterMs;

        Outcome(String kind, String label, int retryAfterMs) {
            this.kind = kind;
            this.label = label;
            this.retryAfterMs = retryAfterMs;
        }
    }
}
