package visual;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A deterministic teaching model for synchronous calls from one service to
 * several downstream services. It shows why bounded timeouts, fallbacks,
 * parallel fan-out and circuit breakers reduce user-visible waiting when one
 * dependency is unavailable.
 */
public class VisualServiceCalls {

    private static final int HISTORY_LIMIT = 10;

    private final String name;
    private final Map<String, Service> services = new LinkedHashMap<>();
    private final List<Map<String, Object>> history = new ArrayList<>();

    private int elapsedMs;
    private int deadlineMs;
    private int savedMs;
    private String strategy = "NONE";
    private String clientStatus = "READY";
    private String responseStatus = "NONE";
    private String responseValue = "";

    public VisualServiceCalls() {
        this("gateway");
    }

    public VisualServiceCalls(String name) {
        this.name = Objects.requireNonNull(name, "name");
        addHistory("client", "CREATE_SCENE", name);
        Trace.event("SERVICE_SCENE_CREATED",
                "Created service-call scene '" + name + "'",
                "Создана сцена service-call '" + name + "'",
                List.of("client"),
                state());
    }

    public VisualServiceCalls deadline(int deadlineMs) {
        if (deadlineMs <= 0) {
            throw new IllegalArgumentException("deadlineMs must be positive");
        }
        this.deadlineMs = deadlineMs;
        addHistory("client", "SET_DEADLINE", deadlineMs + " ms");
        Trace.event("REQUEST_DEADLINE_SET",
                "The client request has an end-to-end deadline of " + deadlineMs + " ms",
                "У клиентского запроса есть общий deadline " + deadlineMs + " ms",
                List.of("client"),
                state());
        return this;
    }

    public VisualServiceCalls service(String serviceName, int latencyMs, boolean available) {
        Objects.requireNonNull(serviceName, "serviceName");
        if (latencyMs < 0) {
            throw new IllegalArgumentException("latencyMs must not be negative");
        }
        Service service = new Service(serviceName, latencyMs, available);
        services.put(serviceName, service);
        addHistory(serviceName, available ? "REGISTER_AVAILABLE" : "REGISTER_UNAVAILABLE", latencyMs + " ms");
        Trace.event("SERVICE_REGISTERED",
                "Registered downstream service '" + serviceName + "' with simulated latency "
                        + latencyMs + " ms" + (available ? "" : " and no response"),
                "Зарегистрирован downstream service '" + serviceName + "' с задержкой "
                        + latencyMs + " ms" + (available ? "" : " и без ответа"),
                List.of("service:" + serviceName),
                state());
        return this;
    }

    public CallResult call(String serviceName, int timeoutMs) {
        Service service = requireService(serviceName);
        validateTimeout(timeoutMs);
        strategy = "SEQUENTIAL";
        clientStatus = "WAITING";
        responseStatus = "WAITING";
        responseValue = "";
        service.timeoutMs = timeoutMs;
        service.status = "CALLING";
        service.result = "";
        service.fallback = "";
        service.lastWaitMs = 0;
        addHistory("client", "CALL", serviceName + " / " + timeoutMs + " ms");
        Trace.event("SERVICE_CALL_STARTED",
                "Calling '" + serviceName + "' with a " + timeoutMs + " ms timeout",
                "Вызов '" + serviceName + "' с timeout " + timeoutMs + " ms",
                List.of("client", "service:" + serviceName),
                state());

        CallResult result = resolve(service, timeoutMs);
        elapsedMs += result.waitMs();
        if (result.success()) {
            clientStatus = "RUNNING";
            responseStatus = "PARTIAL";
            responseValue = result.value();
            addHistory(serviceName, "RETURN_OK", result.waitMs() + " ms");
            Trace.event("SERVICE_CALL_SUCCEEDED",
                    "'" + serviceName + "' answered in " + result.waitMs() + " ms",
                    "'" + serviceName + "' ответил за " + result.waitMs() + " ms",
                    List.of("service:" + serviceName),
                    state());
        } else {
            clientStatus = "DEGRADED";
            responseStatus = "WAITING";
            addHistory(serviceName, "TIMEOUT", result.waitMs() + " ms");
            Trace.event("SERVICE_TIMEOUT",
                    "'" + serviceName + "' did not answer before the " + timeoutMs
                            + " ms timeout, so the client stops waiting",
                    "'" + serviceName + "' не ответил до timeout " + timeoutMs
                            + " ms, поэтому клиент перестает ждать",
                    List.of("client", "service:" + serviceName),
                    state());
        }
        return result;
    }

    public CallResult callWithFallback(String serviceName, int timeoutMs, String fallbackValue) {
        CallResult result = call(serviceName, timeoutMs);
        if (!result.success()) {
            useFallback(serviceName, fallbackValue);
        }
        return result;
    }

    public List<CallResult> callParallel(int timeoutMs, String... serviceNames) {
        validateTimeout(timeoutMs);
        if (serviceNames == null || serviceNames.length == 0) {
            throw new IllegalArgumentException("serviceNames must not be empty");
        }

        strategy = "PARALLEL";
        clientStatus = "WAITING";
        responseStatus = "WAITING";
        responseValue = "";
        addHistory("client", "PARALLEL_FAN_OUT", serviceNames.length + " services");
        Trace.event("PARALLEL_CALLS_STARTED",
                "The client starts independent calls in parallel with one shared timeout of "
                        + timeoutMs + " ms",
                "Клиент запускает независимые вызовы параллельно с общим timeout "
                        + timeoutMs + " ms",
                highlightServices(serviceNames),
                state());

        List<CallResult> results = new ArrayList<>();
        int maxWait = 0;
        int sumWait = 0;
        int failures = 0;
        for (String serviceName : serviceNames) {
            Service service = requireService(serviceName);
            service.timeoutMs = timeoutMs;
            service.fallback = "";
            CallResult result = resolve(service, timeoutMs);
            results.add(result);
            maxWait = Math.max(maxWait, result.waitMs());
            sumWait += result.waitMs();
            if (!result.success()) {
                failures++;
            }
        }

        elapsedMs += maxWait;
        savedMs += Math.max(0, sumWait - maxWait);
        clientStatus = failures == 0 ? "RUNNING" : "DEGRADED";
        responseStatus = failures == 0 ? "OK" : "PARTIAL";
        responseValue = failures == 0 ? "all downstream data" : "partial downstream data";
        addHistory("client", "PARALLEL_JOIN", maxWait + " ms");
        Trace.event("PARALLEL_CALLS_JOINED",
                "Parallel calls joined after " + maxWait + " ms instead of waiting "
                        + sumWait + " ms sequentially",
                "Параллельные вызовы сошлись через " + maxWait
                        + " ms вместо последовательного ожидания " + sumWait + " ms",
                highlightServices(serviceNames),
                state());
        return results;
    }

    public CallResult callWithCircuitBreaker(String serviceName, int timeoutMs,
                                             int failureThreshold, String fallbackValue) {
        if (failureThreshold <= 0) {
            throw new IllegalArgumentException("failureThreshold must be positive");
        }
        Service service = requireService(serviceName);
        validateTimeout(timeoutMs);

        if (service.circuitOpen) {
            strategy = "CIRCUIT_BREAKER";
            clientStatus = "DEGRADED";
            responseStatus = "WAITING";
            service.status = "SKIPPED";
            service.timeoutMs = timeoutMs;
            service.lastWaitMs = 0;
            service.result = "short-circuited";
            savedMs += timeoutMs;
            addHistory("circuit", "SHORT_CIRCUIT", serviceName);
            Trace.event("CIRCUIT_SHORT_CIRCUITED",
                    "Circuit for '" + serviceName + "' is open, so the client fails fast without waiting",
                    "Circuit для '" + serviceName + "' открыт, поэтому клиент быстро отвечает без ожидания",
                    List.of("client", "service:" + serviceName, "circuit:" + serviceName),
                    state());
            useFallback(serviceName, fallbackValue);
            return new CallResult(serviceName, false, 0, "short-circuited");
        }

        strategy = "CIRCUIT_BREAKER";
        CallResult result = call(serviceName, timeoutMs);
        strategy = "CIRCUIT_BREAKER";
        if (!result.success() && service.failures >= failureThreshold && !service.circuitOpen) {
            service.circuitOpen = true;
            service.circuit = "OPEN";
            addHistory("circuit", "OPEN", serviceName);
            Trace.event("CIRCUIT_OPENED",
                    "After " + service.failures + " failed call(s), the circuit for '"
                            + serviceName + "' opens",
                    "После " + service.failures + " неудачных вызовов circuit для '"
                            + serviceName + "' открывается",
                    List.of("service:" + serviceName, "circuit:" + serviceName),
                    state());
        }
        if (!result.success()) {
            useFallback(serviceName, fallbackValue);
        }
        return result;
    }

    public void completeResponse(String value) {
        clientStatus = "RESPONDED";
        responseStatus = "OK";
        responseValue = Objects.requireNonNull(value, "value");
        addHistory("client", "RESPOND", value);
        Trace.event("CLIENT_RESPONDED",
                "The client response is sent after " + elapsedMs + " ms",
                "Ответ клиенту отправлен через " + elapsedMs + " ms",
                List.of("client"),
                state());
    }

    private void useFallback(String serviceName, String fallbackValue) {
        Service service = requireService(serviceName);
        service.status = "FALLBACK";
        service.fallback = Objects.requireNonNull(fallbackValue, "fallbackValue");
        responseStatus = "DEGRADED";
        responseValue = fallbackValue;
        clientStatus = "DEGRADED";
        addHistory("client", "FALLBACK", serviceName);
        Trace.event("SERVICE_FALLBACK_USED",
                "Using fallback for '" + serviceName + "': " + fallbackValue,
                "Используем fallback для '" + serviceName + "': " + fallbackValue,
                List.of("client", "service:" + serviceName),
                state());
    }

    private CallResult resolve(Service service, int timeoutMs) {
        boolean success = service.available && service.latencyMs <= timeoutMs;
        int waitMs = success ? service.latencyMs : timeoutMs;
        service.lastWaitMs = waitMs;
        if (success) {
            service.status = "OK";
            service.result = service.name + " data";
            service.failures = 0;
            service.circuit = service.circuitOpen ? "OPEN" : "CLOSED";
            return new CallResult(service.name, true, waitMs, service.result);
        }

        service.status = "TIMEOUT";
        service.result = service.available ? "too slow" : "unavailable";
        service.failures++;
        return new CallResult(service.name, false, waitMs, service.result);
    }

    private Service requireService(String serviceName) {
        Service service = services.get(serviceName);
        if (service == null) {
            throw new IllegalArgumentException("Unknown service '" + serviceName + "'");
        }
        return service;
    }

    private static void validateTimeout(int timeoutMs) {
        if (timeoutMs <= 0) {
            throw new IllegalArgumentException("timeoutMs must be positive");
        }
    }

    private static List<String> highlightServices(String... serviceNames) {
        List<String> highlight = new ArrayList<>();
        highlight.add("client");
        if (serviceNames != null) {
            for (String serviceName : serviceNames) {
                highlight.add("service:" + serviceName);
            }
        }
        return highlight;
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
        s.put("elapsedMs", elapsedMs);
        s.put("deadlineMs", deadlineMs);
        s.put("savedMs", savedMs);
        s.put("strategy", strategy);

        Map<String, Object> client = new LinkedHashMap<>();
        client.put("status", clientStatus);
        client.put("responseStatus", responseStatus);
        client.put("responseValue", responseValue);
        s.put("client", client);

        List<Object> serviceList = new ArrayList<>();
        for (Service service : services.values()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", service.name);
            item.put("latencyMs", service.latencyMs);
            item.put("timeoutMs", service.timeoutMs);
            item.put("available", service.available);
            item.put("status", service.status);
            item.put("result", service.result);
            item.put("failures", service.failures);
            item.put("circuit", service.circuit);
            item.put("lastWaitMs", service.lastWaitMs);
            item.put("fallback", service.fallback);
            serviceList.add(item);
        }
        s.put("services", serviceList);
        s.put("history", new ArrayList<>(history));
        return s;
    }

    public record CallResult(String serviceName, boolean success, int waitMs, String value) {
    }

    private static final class Service {
        final String name;
        final int latencyMs;
        final boolean available;
        int timeoutMs;
        String status = "IDLE";
        String result = "";
        int failures;
        String circuit = "CLOSED";
        boolean circuitOpen;
        int lastWaitMs;
        String fallback = "";

        Service(String name, int latencyMs, boolean available) {
            this.name = name;
            this.latencyMs = latencyMs;
            this.available = available;
        }
    }
}
