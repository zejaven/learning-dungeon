package visual;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A <em>teaching model</em> of Java's reference strengths and how the garbage
 * collector reacts to them. It is NOT a real heap or GC: it reproduces the one
 * idea an interviewer cares about — that reachability is decided by the
 * <em>strongest</em> live reference to an object — while emitting {@link Trace}
 * events so the UI can visualize allocation, reference creation, GC passes,
 * collection and {@code ReferenceQueue} enqueuing.
 *
 * <p>Reference strengths, from strongest to weakest:
 * <ul>
 *   <li><b>strong</b> — an ordinary reference; keeps the object alive forever.</li>
 *   <li><b>soft</b> — survives a normal GC, cleared only under memory pressure
 *       (the basis of a memory-sensitive cache).</li>
 *   <li><b>weak</b> — cleared at the next GC once no strong/soft ref remains.</li>
 *   <li><b>phantom</b> — never returned by {@code get()}; only used to learn
 *       <em>after</em> the object has been reclaimed, via a {@code ReferenceQueue}.</li>
 * </ul>
 *
 * <p>Differences from the real JVM are intentional and called out in the topic's
 * explanation (e.g. soft clearing depends on the actual GC and free heap; the
 * finalization phase is collapsed into one step here).
 */
public class VisualRefHeap {

    /** Reference strengths, ordered strongest-first by {@code ordinal()}. */
    enum Kind { strong, soft, weak, phantom }

    private static final class Obj {
        final String id;
        final String label;
        boolean collected;

        Obj(String id, String label) {
            this.id = id;
            this.label = label;
        }
    }

    private static final class Ref {
        final String name;
        final Kind kind;
        final String target;
        final boolean queued; // registered with a ReferenceQueue
        boolean cleared;

        Ref(String name, Kind kind, String target, boolean queued) {
            this.name = name;
            this.kind = kind;
            this.target = target;
            this.queued = queued;
        }
    }

    private final Map<String, Obj> objects = new LinkedHashMap<>();
    private final Map<String, Ref> refs = new LinkedHashMap<>();
    private final List<String> queue = new ArrayList<>(); // enqueued reference names
    private boolean memoryLow;

    public VisualRefHeap() {
        Trace.event("REF_HEAP_CREATED",
                "Created an empty heap — no live objects yet",
                "Создана пустая куча — живых объектов пока нет",
                List.of(), state());
    }

    /** Allocates a new object on the heap (no references to it yet). */
    public void allocate(String id, String label) {
        objects.put(id, new Obj(id, label));
        Trace.event("REF_ALLOCATE",
                "Allocated object '" + label + "' on the heap",
                "Выделен объект «" + label + "» в куче",
                List.of("obj:" + id), state());
    }

    /** Creates a strong reference: the object can never be collected while it lives. */
    public void strong(String name, String objId) {
        addRef(name, Kind.strong, objId, false, "REF_STRONG",
                "Strong reference '" + name + "' → '" + label(objId)
                        + "'. While it lives the object is never collected.",
                "Сильная ссылка «" + name + "» → «" + label(objId)
                        + "». Пока она жива, объект не будет собран.");
    }

    /** Creates a soft reference: kept until the JVM runs low on memory (cache). */
    public void soft(String name, String objId) {
        addRef(name, Kind.soft, objId, false, "REF_SOFT",
                "Soft reference '" + name + "' → '" + label(objId)
                        + "'. Survives normal GC; cleared only under memory pressure.",
                "Мягкая ссылка «" + name + "» → «" + label(objId)
                        + "». Переживает обычный GC; очищается только при нехватке памяти.");
    }

    /** Creates a weak reference: cleared at the next GC if nothing stronger holds it. */
    public void weak(String name, String objId) {
        addRef(name, Kind.weak, objId, false, "REF_WEAK",
                "Weak reference '" + name + "' → '" + label(objId)
                        + "'. Cleared at the next GC once no strong/soft ref remains.",
                "Слабая ссылка «" + name + "» → «" + label(objId)
                        + "». Очищается на ближайшем GC, если нет сильной/мягкой ссылки.");
    }

    /** Creates a phantom reference registered with a queue; get() is always null. */
    public void phantom(String name, String objId) {
        addRef(name, Kind.phantom, objId, true, "REF_PHANTOM",
                "Phantom reference '" + name + "' → '" + label(objId)
                        + "'. get() is always null; it is enqueued after collection for cleanup.",
                "Фантомная ссылка «" + name + "» → «" + label(objId)
                        + "». get() всегда null; ставится в очередь после сборки для очистки.");
    }

    private void addRef(String name, Kind kind, String objId, boolean queued,
                        String event, String descEn, String descRu) {
        refs.put(name, new Ref(name, kind, objId, queued));
        Trace.event(event, descEn, descRu,
                List.of("ref:" + name, "obj:" + objId), state());
    }

    /** Drops a reference, as if you assigned the variable to {@code null}. */
    public void drop(String name) {
        Ref r = refs.get(name);
        if (r == null) return;
        r.cleared = true;
        Trace.event("REF_DROP",
                "Dropped " + r.kind + " reference '" + name + "' (variable set to null)",
                "Сброшена " + ruKind(r.kind) + " ссылка «" + name + "» (переменная = null)",
                List.of("ref:" + name), state());
    }

    /** Reads the referent through a reference, returning its label or null. */
    public String get(String name) {
        Ref r = refs.get(name);
        String result;
        if (r == null) {
            result = null;
        } else if (r.kind == Kind.phantom) {
            result = null; // phantom.get() is ALWAYS null in Java
        } else if (r.cleared) {
            result = null;
        } else {
            Obj o = objects.get(r.target);
            result = (o != null && !o.collected) ? o.label : null;
        }
        boolean phantom = r != null && r.kind == Kind.phantom;
        String enHit = result != null ? "returned '" + result + "' (hit)"
                : phantom ? "returned null (phantom.get() is always null)" : "returned null (referent gone)";
        String ruHit = result != null ? "вернул «" + result + "» (попадание)"
                : phantom ? "вернул null (phantom.get() всегда null)" : "вернул null (объекта больше нет)";
        Trace.event("REF_GET",
                "get('" + name + "') " + enHit,
                "get(«" + name + "») " + ruHit,
                r == null ? List.of() : List.of("ref:" + name, "obj:" + r.target), state());
        return result;
    }

    /** Runs a normal garbage collection: strong and soft references survive. */
    public void gc() {
        runGc(false);
    }

    /** Runs a GC under memory pressure: soft references are also cleared. */
    public void gcUnderMemoryPressure() {
        runGc(true);
    }

    private void runGc(boolean pressure) {
        memoryLow = pressure;
        Trace.event(pressure ? "REF_GC_PRESSURE" : "REF_GC",
                pressure
                        ? "GC under memory pressure — soft references will be cleared to free heap"
                        : "Normal GC — strong and soft references survive, weak/phantom-only objects are collected",
                pressure
                        ? "GC при нехватке памяти — мягкие ссылки будут очищены, чтобы освободить кучу"
                        : "Обычный GC — сильные и мягкие ссылки выживают, объекты только под weak/phantom собираются",
                List.of(), state());

        for (Obj o : new ArrayList<>(objects.values())) {
            if (o.collected) continue;
            String reach = reachability(o.id);
            boolean survives = switch (reach) {
                case "strong" -> true;
                case "soft" -> !pressure;
                default -> false; // weak, phantom, unreachable
            };
            if (!survives) {
                collect(o, reach);
            }
        }
        memoryLow = false;
    }

    private void collect(Obj o, String reach) {
        o.collected = true;
        List<String> enqueued = new ArrayList<>();
        for (Ref r : refs.values()) {
            if (!r.target.equals(o.id) || r.cleared || r.kind == Kind.strong) continue;
            r.cleared = true;
            if (r.queued && (r.kind == Kind.weak || r.kind == Kind.phantom)) {
                queue.add(r.name);
                enqueued.add(r.name);
            }
        }
        Trace.event("REF_COLLECT",
                "GC reclaimed '" + o.label + "' (" + reach
                        + "-reachable only) and cleared its references",
                "GC собрал «" + o.label + "» (достижим только как " + reach
                        + ") и очистил его ссылки",
                List.of("obj:" + o.id), state());
        for (String name : enqueued) {
            Trace.event("REF_ENQUEUE",
                    "Reference '" + name + "' was enqueued — cleanup code can now run",
                    "Ссылка «" + name + "» поставлена в очередь — теперь можно выполнить очистку",
                    List.of("ref:" + name), state());
        }
    }

    /** The strongest live reference to an object decides its reachability. */
    private String reachability(String objId) {
        Obj o = objects.get(objId);
        if (o == null || o.collected) return "collected";
        Kind best = null;
        for (Ref r : refs.values()) {
            if (r.cleared || !r.target.equals(objId)) continue;
            if (best == null || r.kind.ordinal() < best.ordinal()) best = r.kind;
        }
        return best == null ? "unreachable" : best.name();
    }

    private String label(String objId) {
        Obj o = objects.get(objId);
        return o == null ? objId : o.label;
    }

    private static String ruKind(Kind k) {
        return switch (k) {
            case strong -> "сильная";
            case soft -> "мягкая";
            case weak -> "слабая";
            case phantom -> "фантомная";
        };
    }

    /** Builds the JSON-serializable snapshot consumed by the visualizer. */
    private Object state() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("memoryLow", memoryLow);

        List<Object> objList = new ArrayList<>();
        for (Obj o : objects.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", o.id);
            m.put("label", o.label);
            m.put("collected", o.collected);
            m.put("reachability", reachability(o.id));
            objList.add(m);
        }
        s.put("objects", objList);

        List<Object> refList = new ArrayList<>();
        for (Ref r : refs.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", r.name);
            m.put("kind", r.kind.name());
            m.put("target", r.target);
            m.put("cleared", r.cleared);
            m.put("queued", r.queued);
            refList.add(m);
        }
        s.put("references", refList);

        s.put("queue", new ArrayList<Object>(queue));
        return s;
    }
}
