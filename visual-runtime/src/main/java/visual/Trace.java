package visual;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Emits learning "trace events" to stdout. Each event is printed on its own
 * line prefixed with {@code @@TRACE@@} followed by a JSON object. The backend
 * separates these lines from the program's normal output and forwards the
 * decoded events to the frontend, which replays them step by step.
 *
 * <p>This class is intentionally dependency-free and hand-rolls JSON so that
 * placing visual-runtime on the sandbox classpath never drags a third-party
 * library into user code.
 *
 * <p>Event envelope:
 * <pre>
 * { "step": 1, "event": "HASHMAP_PUT", "description": "...",
 *   "highlight": ["bucket:0"], "state": { ... topic specific ... } }
 * </pre>
 */
public final class Trace {

    public static final String PREFIX = "@@TRACE@@";

    private static final AtomicInteger STEP = new AtomicInteger(0);

    private Trace() {
    }

    /** Emits a single trace event. {@code state} is any JSON-serializable value. */
    public static void event(String event, String description, List<String> highlight, Object state) {
        StringBuilder sb = new StringBuilder(128);
        sb.append('{');
        writeKey(sb, "step").append(STEP.incrementAndGet());
        sb.append(',');
        writeKey(sb, "event");
        writeString(sb, event);
        sb.append(',');
        writeKey(sb, "description");
        writeString(sb, description);
        sb.append(',');
        writeKey(sb, "highlight");
        writeValue(sb, highlight == null ? List.of() : highlight);
        sb.append(',');
        writeKey(sb, "state");
        writeValue(sb, state);
        sb.append('}');

        // Single println so the line is atomic relative to the backend reader.
        System.out.println(PREFIX + sb);
    }

    private static StringBuilder writeKey(StringBuilder sb, String key) {
        writeString(sb, key);
        return sb.append(':');
    }

    @SuppressWarnings("unchecked")
    private static void writeValue(StringBuilder sb, Object v) {
        if (v == null) {
            sb.append("null");
        } else if (v instanceof String s) {
            writeString(sb, s);
        } else if (v instanceof Number || v instanceof Boolean) {
            sb.append(v);
        } else if (v instanceof Map<?, ?> m) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                writeString(sb, String.valueOf(e.getKey()));
                sb.append(':');
                writeValue(sb, e.getValue());
            }
            sb.append('}');
        } else if (v instanceof Iterable<?> it) {
            sb.append('[');
            boolean first = true;
            for (Object o : it) {
                if (!first) sb.append(',');
                first = false;
                writeValue(sb, o);
            }
            sb.append(']');
        } else {
            // Fallback: treat as a string.
            writeString(sb, String.valueOf(v));
        }
    }

    private static void writeString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }
}
