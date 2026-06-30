package visual;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A deterministic teaching model for Java memory leaks. It tracks heap objects
 * and the named references ("holders") that keep them reachable from a GC root.
 *
 * <p>An object survives a {@link #gc()} cycle while any holder still references
 * it. Short-lived holders (a method scope, a stack frame) are transient roots;
 * a {@link #longLivedRoot(String) long-lived root} (a static field, a listener
 * registry, a ThreadLocal) outlives the work that created the object. When the
 * creating scope ends but a long-lived root still references an object that is
 * no longer needed, the object cannot be collected — that retained garbage is
 * the leak this model surfaces as a {@code LEAK_DETECTED} event.
 */
public class VisualMemoryLeak {

    private static final int HISTORY_LIMIT = 8;

    private final String name;
    private final Map<String, String> roots = new LinkedHashMap<>();
    private final Map<String, Obj> objects = new LinkedHashMap<>();
    private final List<Map<String, Object>> history = new ArrayList<>();
    private int leakCount;

    public VisualMemoryLeak() {
        this("heap");
    }

    public VisualMemoryLeak(String name) {
        this.name = Objects.requireNonNull(name, "name");
        Trace.event("MODEL_CREATED",
                "Created heap scene '" + name + "'. Objects live while a GC root still references them.",
                "Создана сцена кучи '" + name + "'. Объекты живут, пока на них ссылается GC root.",
                List.of(),
                state());
    }

    /** Declare a long-lived GC root (a static field, listener list, ThreadLocal, ...). */
    public void longLivedRoot(String root) {
        Objects.requireNonNull(root, "root");
        roots.put(root, "LONG_LIVED");
        addHistory("ROOT_DECLARED", root, "long-lived");
        Trace.event("ROOT_DECLARED",
                "Declared long-lived root '" + root + "'. Anything it references outlives a single method.",
                "Объявлен долгоживущий root '" + root + "'. То, на что он ссылается, переживёт один метод.",
                List.of("root:" + root),
                state());
    }

    /** Allocate a new object, referenced by {@code holder}. */
    public void allocate(String id, String label, String holder) {
        Objects.requireNonNull(id, "id");
        registerScope(holder);
        Obj obj = new Obj(id, label, holder);
        obj.holders.add(holder);
        objects.put(id, obj);
        addHistory("ALLOCATE", id, holder);
        Trace.event("ALLOCATE",
                "Allocated " + label + " (" + id + "), referenced by '" + holder + "'.",
                "Создан " + label + " (" + id + "), на него ссылается '" + holder + "'.",
                List.of("object:" + id, "root:" + holder),
                state());
    }

    /** Add another reference to an existing object (e.g. store it into a long-lived root). */
    public void addReference(String id, String holder) {
        registerScope(holder);
        Obj obj = require(id);
        obj.holders.add(holder);
        addHistory("REFERENCE_ADDED", id, holder);
        Trace.event("REFERENCE_ADDED",
                "'" + holder + "' now also references " + obj.label + " (" + id + ").",
                "'" + holder + "' теперь тоже ссылается на " + obj.label + " (" + id + ").",
                List.of("object:" + id, "root:" + holder),
                state());
    }

    /** Explicitly drop one reference (remove from the cache, unregister the listener, ThreadLocal.remove()). */
    public void dropReference(String id, String holder) {
        Obj obj = require(id);
        obj.holders.remove(holder);
        if (holder.equals(obj.creator)) {
            obj.escaped = true;
        }
        addHistory("REFERENCE_REMOVED", id, holder);
        Trace.event("REFERENCE_REMOVED",
                "'" + holder + "' releases its reference to " + obj.label + " (" + id + ").",
                "'" + holder + "' отпускает ссылку на " + obj.label + " (" + id + ").",
                List.of("object:" + id, "root:" + holder),
                state());
    }

    /** The transient holder's frame ends: every reference it held is dropped at once. */
    public void exitScope(String holder) {
        Objects.requireNonNull(holder, "holder");
        for (Obj obj : objects.values()) {
            if (!obj.collected && obj.holders.remove(holder) && holder.equals(obj.creator)) {
                obj.escaped = true;
            }
        }
        addHistory("SCOPE_EXIT", holder, "");
        Trace.event("SCOPE_EXIT",
                "Scope '" + holder + "' ended; the references on its stack frame are gone.",
                "Область видимости '" + holder + "' завершилась; ссылки её stack frame исчезли.",
                List.of("root:" + holder),
                state());
    }

    /** Run a GC cycle: free every unreachable object; report objects retained only by long-lived roots. */
    public void gc() {
        Trace.event("GC_RUN",
                "GC runs: it keeps every object still reachable from a GC root and frees the rest.",
                "Запускается GC: он сохраняет каждый объект, достижимый от GC root, и освобождает остальные.",
                List.of(),
                state());
        for (Obj obj : objects.values()) {
            if (obj.collected) {
                continue;
            }
            if (obj.holders.isEmpty()) {
                obj.collected = true;
                addHistory("GC_COLLECTED", obj.id, "");
                Trace.event("GC_COLLECTED",
                        obj.label + " (" + obj.id + ") has no references left and is collected.",
                        obj.label + " (" + obj.id + ") больше не имеет ссылок и собирается GC.",
                        List.of("object:" + obj.id),
                        state());
            } else if (obj.escaped && allLongLived(obj.holders)) {
                if (!obj.leaked) {
                    obj.leaked = true;
                    leakCount++;
                }
                addHistory("LEAK_DETECTED", obj.id, String.join(",", obj.holders));
                Trace.event("LEAK_DETECTED",
                        "Leak: " + obj.label + " (" + obj.id + ") is no longer used but "
                                + String.join(", ", obj.holders) + " still holds it, so GC cannot free it.",
                        "Утечка: " + obj.label + " (" + obj.id + ") больше не нужен, но "
                                + String.join(", ", obj.holders) + " всё ещё держит его, поэтому GC не может его освободить.",
                        List.of("object:" + obj.id),
                        state());
            }
        }
    }

    public int leakCount() {
        return leakCount;
    }

    public int liveCount() {
        int live = 0;
        for (Obj obj : objects.values()) {
            if (!obj.collected) {
                live++;
            }
        }
        return live;
    }

    private void registerScope(String holder) {
        Objects.requireNonNull(holder, "holder");
        roots.putIfAbsent(holder, "SCOPE");
    }

    private Obj require(String id) {
        Obj obj = objects.get(id);
        if (obj == null) {
            throw new IllegalArgumentException("unknown object: " + id);
        }
        return obj;
    }

    private boolean allLongLived(Set<String> holders) {
        for (String holder : holders) {
            if (!"LONG_LIVED".equals(roots.get(holder))) {
                return false;
            }
        }
        return !holders.isEmpty();
    }

    private void addHistory(String action, String target, String detail) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("action", action);
        item.put("target", target);
        item.put("detail", detail);
        history.add(item);
        if (history.size() > HISTORY_LIMIT) {
            history.remove(0);
        }
    }

    private Object state() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("name", name);
        s.put("liveCount", liveCount());
        s.put("leakCount", leakCount);

        List<Object> rootList = new ArrayList<>();
        for (Map.Entry<String, String> entry : roots.entrySet()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", entry.getKey());
            item.put("kind", entry.getValue());
            rootList.add(item);
        }
        s.put("roots", rootList);

        List<Object> objectList = new ArrayList<>();
        for (Obj obj : objects.values()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", obj.id);
            item.put("label", obj.label);
            item.put("holders", new ArrayList<>(obj.holders));
            item.put("collected", obj.collected);
            item.put("leaked", obj.leaked);
            item.put("escaped", obj.escaped);
            objectList.add(item);
        }
        s.put("objects", objectList);
        s.put("history", new ArrayList<>(history));
        return s;
    }

    private static final class Obj {
        final String id;
        final String label;
        final String creator;
        final Set<String> holders = new LinkedHashSet<>();
        boolean collected;
        boolean leaked;
        boolean escaped;

        Obj(String id, String label, String creator) {
            this.id = id;
            this.label = label;
            this.creator = creator;
        }
    }
}
