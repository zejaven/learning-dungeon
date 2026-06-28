package visual;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A <em>teaching model</em> of what it costs to build one big string out of many
 * small pieces — the interview question "how would you concatenate a million
 * strings without wasting memory?". It is NOT the JDK implementation; it
 * reproduces the ideas an interviewer probes for and emits {@link Trace} events
 * so the UI can visualize the cost of each strategy.
 *
 * <p>Two strategies are modelled:
 *
 * <ul>
 *   <li><b>{@code +} in a loop</b> ({@link #concatLoop}): because a {@code String}
 *   is immutable, {@code result = result + piece} allocates a <em>brand-new</em>
 *   {@code String} every iteration and copies <em>all</em> characters seen so
 *   far. The previous object becomes garbage. Total copy work is
 *   O(n&sup2;) and the loop leaves a trail of dead objects — the memory problem.
 *   <li><b>{@link StringBuilder}</b> ({@link #builderLoop}): one mutable
 *   {@code char[]} buffer is reused; {@code append} copies only the new piece.
 *   When the buffer is full it grows (the real JDK uses {@code (cap << 1) + 2}),
 *   reallocating once and copying the existing characters. Growth happens only
 *   O(log n) times, so the amortized work is O(n) and almost no garbage is left.
 *   Pre-sizing the builder ({@link #builderLoop(String, int, int)}) avoids the
 *   reallocations entirely.
 * </ul>
 *
 * <p>The model tracks a tiny "heap" of live objects plus running counters
 * (objects allocated, characters copied, garbage objects and garbage characters)
 * so the visualizer can contrast the two strategies side by side. Differences
 * from the real JVM are intentional and called out in the topic explanation
 * (no real GC timing; compile-time constant folding of {@code "a" + "b"} is not
 * modelled; the displayed content is the literal characters).
 */
public class VisualStringConcat {

    /** Real Java default StringBuilder capacity. */
    private static final int DEFAULT_CAPACITY = 16;

    private final List<Obj> live = new ArrayList<>();
    private String strategy = "CONCAT";
    private int iteration;
    private int length;
    private int capacity = -1;     // -1 = not applicable (CONCAT)
    private int allocations;
    private long copies;           // cumulative characters copied (the "work")
    private int garbageObjects;
    private long garbageChars;
    private int counter;

    /**
     * Builds a string by repeatedly doing {@code result = result + piece} — the
     * naive approach. Each iteration allocates a new {@code String} and copies
     * every character accumulated so far; the previous {@code String} is dropped
     * as garbage.
     */
    public void concatLoop(String piece, int times) {
        reset("CONCAT");
        Trace.event("CONCAT_START",
                "result = \"\" — building a String with result = result + piece in a loop ("
                        + times + " pieces of \"" + piece + "\")",
                "result = \"\" — собираем строку через result = result + piece в цикле ("
                        + times + " кусков \"" + piece + "\")",
                List.of(), state(piece));

        StringBuilder content = new StringBuilder();
        for (int i = 0; i < times; i++) {
            int oldLen = length;
            // The previous result object (if any) is now unreachable: garbage.
            if (!live.isEmpty()) {
                garbageObjects += live.size();
                garbageChars += oldLen;
                live.clear();
            }
            content.append(piece);
            length = content.length();
            // The new String copies ALL characters seen so far.
            copies += length;
            allocations++;            // a new String (its backing array shares the count)
            iteration = i + 1;
            String id = "str" + (++counter);
            live.add(new Obj(id, "String", content.toString(), length, -1));
            Trace.event("CONCAT_STEP",
                    "Iteration " + iteration + ": a NEW String of length " + length
                            + " is allocated and " + length + " chars are copied; the previous String ("
                            + oldLen + " chars) becomes garbage",
                    "Итерация " + iteration + ": выделяется НОВАЯ строка длиной " + length
                            + ", копируется " + length + " символов; предыдущая строка ("
                            + oldLen + " символов) становится мусором",
                    List.of("obj:" + id, "metric:copies", "metric:garbage"), state(piece));
        }

        Trace.event("CONCAT_DONE",
                "Done: " + allocations + " String objects allocated, " + copies
                        + " chars copied in total, " + garbageObjects
                        + " objects left as garbage — work grows as O(n^2)",
                "Готово: выделено " + allocations + " объектов String, всего скопировано "
                        + copies + " символов, " + garbageObjects
                        + " объектов осталось мусором — работа растёт как O(n^2)",
                List.of("metric:copies", "metric:garbage"), state(piece));
    }

    /** {@link #builderLoop(String, int, int)} with the JDK default capacity (16). */
    public void builderLoop(String piece, int times) {
        builderLoop(piece, times, 0);
    }

    /**
     * Builds a string with a {@link StringBuilder}. With {@code initialCapacity}
     * &gt; 0 the buffer is pre-sized (a {@code BUILDER_PRESIZE} event); otherwise
     * it starts at the JDK default of 16. Each {@code append} copies only the new
     * piece; when the buffer is full it grows once (reallocating and copying the
     * existing characters), emitting {@code BUILDER_GROW}.
     */
    public void builderLoop(String piece, int times, int initialCapacity) {
        reset("BUILDER");
        boolean presized = initialCapacity > 0;
        capacity = presized ? initialCapacity : DEFAULT_CAPACITY;
        allocations++;
        copies += 0;
        String bufId = "buf" + (++counter);
        live.add(new Obj(bufId, "char[]", "", 0, capacity));
        if (presized) {
            Trace.event("BUILDER_PRESIZE",
                    "new StringBuilder(" + capacity + ") — the buffer is pre-sized for the whole result, "
                            + "so it never has to grow",
                    "new StringBuilder(" + capacity + ") — буфер заранее рассчитан на весь результат, "
                            + "поэтому ему не придётся расти",
                    List.of("obj:" + bufId, "metric:capacity"), state(piece));
        } else {
            Trace.event("BUILDER_START",
                    "new StringBuilder() — one reusable char[] buffer, default capacity " + capacity,
                    "new StringBuilder() — один переиспользуемый буфер char[], ёмкость по умолчанию " + capacity,
                    List.of("obj:" + bufId, "metric:capacity"), state(piece));
        }

        StringBuilder content = new StringBuilder();
        for (int i = 0; i < times; i++) {
            int needed = length + piece.length();
            if (needed > capacity) {
                // Grow: allocate a bigger buffer, copy existing chars, drop the old one.
                int oldCap = capacity;
                int newCap = (capacity << 1) + 2;     // the real JDK growth formula
                if (newCap < needed) newCap = needed;
                capacity = newCap;
                copies += length;                     // existing chars copied into the new buffer
                garbageObjects += 1;                  // old char[] is now garbage
                garbageChars += oldCap;
                allocations++;
                live.clear();
                String grownId = "buf" + (++counter);
                live.add(new Obj(grownId, "char[]", content.toString(), length, capacity));
                Trace.event("BUILDER_GROW",
                        "Buffer full: capacity grows " + oldCap + " -> " + newCap
                                + "; " + length + " existing chars are copied once and the old buffer is dropped",
                        "Буфер заполнен: ёмкость растёт " + oldCap + " -> " + newCap
                                + "; " + length + " уже накопленных символов копируются один раз, старый буфер выбрасывается",
                        List.of("obj:" + grownId, "metric:capacity", "metric:garbage"), state(piece));
            }

            content.append(piece);
            length = content.length();
            copies += piece.length();                 // only the NEW piece is copied
            iteration = i + 1;
            Obj buf = live.get(0);
            buf.value = content.toString();
            buf.len = length;
            Trace.event("BUILDER_APPEND",
                    "append(\"" + piece + "\") — only " + piece.length()
                            + " new chars are copied into the SAME buffer (length " + length
                            + " / capacity " + capacity + "); no new object",
                    "append(\"" + piece + "\") — в ТОТ ЖЕ буфер копируется лишь " + piece.length()
                            + " новых символов (длина " + length + " / ёмкость " + capacity
                            + "); новый объект не создаётся",
                    List.of("obj:" + buf.id, "metric:copies"), state(piece));
        }

        Trace.event("BUILDER_DONE",
                "Done: " + allocations + " buffer(s) allocated, " + copies
                        + " chars copied in total, " + garbageObjects
                        + " buffer(s) left as garbage — work is O(n)",
                "Готово: выделено буферов: " + allocations + ", всего скопировано "
                        + copies + " символов, мусором осталось буферов: " + garbageObjects
                        + " — работа линейна, O(n)",
                List.of("metric:copies", "metric:garbage"), state(piece));
    }

    // --- internals -------------------------------------------------------

    private void reset(String mode) {
        strategy = mode;
        live.clear();
        iteration = 0;
        length = 0;
        capacity = -1;
        allocations = 0;
        copies = 0;
        garbageObjects = 0;
        garbageChars = 0;
    }

    /** Builds the JSON-serializable snapshot consumed by the visualizer. */
    private Object state(String piece) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("strategy", strategy);
        s.put("iteration", iteration);
        s.put("piece", piece);
        s.put("length", length);
        s.put("capacity", capacity < 0 ? null : capacity);
        s.put("allocations", allocations);
        s.put("copies", copies);
        s.put("garbageObjects", garbageObjects);
        s.put("garbageChars", garbageChars);

        List<Object> liveObjs = new ArrayList<>();
        for (Obj o : live) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", o.id);
            m.put("kind", o.kind);
            m.put("value", o.value);
            m.put("len", o.len);
            m.put("capacity", o.capacity < 0 ? null : o.capacity);
            liveObjs.add(m);
        }
        s.put("live", liveObjs);
        return s;
    }

    private static final class Obj {
        final String id;
        final String kind;   // "String" | "char[]"
        String value;
        int len;
        final int capacity;  // char[] capacity; -1 for a String

        Obj(String id, String kind, String value, int len, int capacity) {
            this.id = id;
            this.kind = kind;
            this.value = value;
            this.len = len;
            this.capacity = capacity;
        }
    }
}
