package com.interviewlearning.generation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A single in-progress (or finished) topic generation. It buffers every emitted
 * SSE event so a client that connects late — or reconnects after closing the
 * window — can replay the whole history and then keep receiving live events.
 *
 * <p>Thread-safety: all mutation and attach happen under the instance monitor, so
 * a subscriber never misses or duplicates an event that arrives mid-attach.
 */
public class GenerationTask {

    private static final Logger log = LoggerFactory.getLogger(GenerationTask.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private record Event(String name, String data) {
    }

    private final String id;
    private final String key;
    private volatile String status = "running"; // running | done | error
    private final List<Event> history = new ArrayList<>();
    private final Set<SseEmitter> subscribers = new LinkedHashSet<>();

    public GenerationTask(String id, String key) {
        this.id = id;
        this.key = key;
    }

    public String id() {
        return id;
    }

    public String key() {
        return key;
    }

    public String status() {
        return status;
    }

    public boolean isTerminal() {
        return !"running".equals(status);
    }

    /** Buffers and broadcasts a normalized AI stream line. */
    public synchronized void addAi(String line) {
        add(new Event("ai", line));
    }

    /** Buffers and broadcasts a status change; completes subscribers when terminal. */
    public synchronized void addStatus(String newStatus, String message) {
        if ("done".equals(newStatus) || "error".equals(newStatus)) {
            this.status = newStatus;
        }
        String json;
        try {
            json = MAPPER.writeValueAsString(Map.of(
                    "status", newStatus, "message", String.valueOf(message)));
        } catch (JsonProcessingException e) {
            // Never hand-build JSON with the message inside: a Windows path or
            // control characters in it would produce invalid JSON.
            json = "{\"status\":\"" + newStatus + "\"}";
        }
        add(new Event("status", json));
        if (isTerminal()) {
            for (SseEmitter emitter : new ArrayList<>(subscribers)) {
                try {
                    emitter.complete();
                } catch (Exception ignored) {
                    // already closed
                }
            }
            subscribers.clear();
            notifyAll();
        }
    }

    /**
     * Blocks until the task reaches a terminal status ({@code done}/{@code error}).
     *
     * @return true when terminal; false when the timeout elapsed first
     */
    public synchronized boolean awaitTerminal(long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (!isTerminal()) {
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) {
                return false;
            }
            wait(remaining);
        }
        return true;
    }

    private void add(Event event) {
        history.add(event);
        for (SseEmitter emitter : new ArrayList<>(subscribers)) {
            if (!send(emitter, event)) {
                subscribers.remove(emitter);
            }
        }
    }

    /**
     * Creates an emitter that replays the buffered history and then, if the task
     * is still running, keeps receiving live events.
     */
    public synchronized SseEmitter attach(long timeoutMillis) {
        SseEmitter emitter = new SseEmitter(timeoutMillis);
        for (Event event : history) {
            if (!send(emitter, event)) {
                return emitter; // client already gone
            }
        }
        if (isTerminal()) {
            try {
                emitter.complete();
            } catch (Exception ignored) {
                // ignore
            }
        } else {
            subscribers.add(emitter);
            emitter.onCompletion(() -> removeSubscriber(emitter));
            emitter.onTimeout(() -> removeSubscriber(emitter));
            emitter.onError(t -> removeSubscriber(emitter));
        }
        return emitter;
    }

    private synchronized void removeSubscriber(SseEmitter emitter) {
        subscribers.remove(emitter);
    }

    private boolean send(SseEmitter emitter, Event event) {
        try {
            emitter.send(SseEmitter.event().name(event.name()).data(event.data()));
            return true;
        } catch (IOException | IllegalStateException e) {
            log.debug("Dropping dead generation subscriber: {}", e.getMessage());
            return false;
        }
    }
}
