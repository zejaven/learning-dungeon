package visual;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A <em>teaching model</em> of <strong>what a Java variable stores and where</strong>.
 * It is NOT a real JVM: it reproduces the mental model interviewers ask about —
 * a primitive variable holds its <em>value</em> directly in its stack slot, while
 * a reference variable holds only a <em>reference</em> (a handle) to an object that
 * lives on the heap. From that single idea everything else follows: copying a
 * primitive copies the value (independent), copying a reference copies the handle
 * (aliasing — two names for one object), reassigning a reference repoints the name
 * and can leave the old object as garbage, and method arguments are passed by value
 * (the value, or the reference value, is copied into the callee's frame).
 *
 * <p>The model keeps a tiny call stack of {@link Frame}s (each a set of named
 * variables) and a heap of {@link Obj}ects, and emits {@link Trace} events so the
 * UI can draw stack slots, references pointing at heap objects, and objects that
 * became garbage. Differences from the real JVM are intentional and explained in
 * the topic: object fields here are plain values (no object graphs), there is no
 * real GC timing, and escape analysis / scalar replacement are not modelled.
 */
public class VisualMemory {

    /** The call stack: index 0 is the bottom-most frame ("main"). */
    private final List<Frame> stack = new ArrayList<>();
    /** Object id -> heap object, in allocation order. */
    private final Map<String, Obj> heap = new LinkedHashMap<>();

    private int counter;

    public VisualMemory() {
        stack.add(new Frame("main"));
        Trace.event("MEM_SCENE",
                "Empty scene: a single stack frame main(), nothing on the heap yet",
                "Пустая сцена: один кадр стека main(), в куче пока ничего нет",
                List.of(), state());
    }

    /**
     * {@code int a = 5;} — a primitive variable stores its value directly in its
     * stack slot. No heap object is involved.
     */
    public void primitive(String name, String type, String value) {
        top().vars.put(name, Var.primitive(type, value));
        Trace.event("MEM_PRIMITIVE",
                type + " " + name + " = " + value + " — the value " + value
                        + " is stored directly in " + name + "'s stack slot",
                type + " " + name + " = " + value + " — значение " + value
                        + " лежит прямо в слоте переменной " + name + " на стеке",
                List.of("var:" + name), state());
    }

    /**
     * {@code int b = a;} — copies the primitive <em>value</em> into a new slot.
     * The two slots are independent: changing one never affects the other.
     */
    public void copyPrimitive(String target, String type, String source) {
        String value = top().vars.get(source).value;
        top().vars.put(target, Var.primitive(type, value));
        Trace.event("MEM_COPY_PRIMITIVE",
                type + " " + target + " = " + source + " — the value " + value
                        + " is COPIED; " + target + " and " + source + " are independent slots",
                type + " " + target + " = " + source + " — значение " + value
                        + " КОПИРУЕТСЯ; " + target + " и " + source + " — независимые слоты",
                List.of("var:" + target, "var:" + source), state());
    }

    /** {@code b = 99;} — overwrite a primitive slot's value in place. */
    public void setPrimitive(String name, String value) {
        Var v = top().vars.get(name);
        v.value = value;
        Trace.event("MEM_SET_PRIMITIVE",
                name + " = " + value + " — only " + name + "'s own slot changes; any copy is untouched",
                name + " = " + value + " — меняется только слот " + name + "; копия не затрагивается",
                List.of("var:" + name), state());
    }

    /**
     * {@code Point p = new Point(...);} — allocates an object on the heap and stores
     * a <em>reference</em> to it in the variable's stack slot. Fields are given as
     * {@code "name=value"} pairs.
     */
    public void newObject(String var, String type, String... fields) {
        String id = allocate(type, fields);
        top().vars.put(var, Var.reference(type, id));
        Trace.event("MEM_NEW",
                type + " " + var + " = new " + type + "(...) — the object lives on the heap (" + id
                        + "); " + var + " holds only a reference to it",
                type + " " + var + " = new " + type + "(...) — объект лежит в куче (" + id
                        + "); " + var + " хранит лишь ссылку на него",
                List.of("var:" + var, "obj:" + id), state());
    }

    /**
     * {@code Point q = p;} — copies the <em>reference</em>, not the object. Now two
     * variables point at the SAME heap object (aliasing).
     */
    public void copyReference(String target, String type, String source) {
        String id = top().vars.get(source).ref;
        top().vars.put(target, Var.reference(type, id));
        Trace.event("MEM_COPY_REFERENCE",
                type + " " + target + " = " + source + " — the reference is COPIED; " + target
                        + " and " + source + " now point at the SAME object (" + id + ")",
                type + " " + target + " = " + source + " — копируется ССЫЛКА; " + target
                        + " и " + source + " теперь указывают на ТОТ ЖЕ объект (" + id + ")",
                List.of("var:" + target, "var:" + source, "obj:" + id), state());
    }

    /**
     * {@code q.field = value;} — mutates a field of the object the variable points
     * at. Every alias of that object sees the change.
     */
    public void mutateField(String var, String field, String value) {
        String id = top().vars.get(var).ref;
        heap.get(id).fields.put(field, value);
        Trace.event("MEM_MUTATE",
                var + "." + field + " = " + value + " — the object on the heap (" + id
                        + ") changes; every reference to it sees the new value",
                var + "." + field + " = " + value + " — меняется объект в куче (" + id
                        + "); это видят все ссылки на него",
                List.of("var:" + var, "obj:" + id), state());
    }

    /**
     * {@code p = new Point(...);} — repoints a reference at a brand-new object. The
     * previously referenced object may now be unreachable (garbage).
     */
    public void reassignNew(String var, String type, String... fields) {
        String id = allocate(type, fields);
        top().vars.put(var, Var.reference(type, id));
        Trace.event("MEM_REASSIGN",
                var + " = new " + type + "(...) — " + var + " now points at a new object (" + id
                        + "); the old one may become garbage",
                var + " = new " + type + "(...) — " + var + " теперь указывает на новый объект (" + id
                        + "); прежний может стать мусором",
                List.of("var:" + var, "obj:" + id), state());
    }

    /** {@code p = null;} — the variable holds null: it references no object at all. */
    public void setNull(String var) {
        top().vars.put(var, Var.reference(top().vars.get(var).type, null));
        Trace.event("MEM_NULL",
                var + " = null — " + var + " now references no object; the previous object may become garbage",
                var + " = null — " + var + " больше ни на что не ссылается; прежний объект может стать мусором",
                List.of("var:" + var), state());
    }

    /**
     * Models a method call {@code method(arg)} whose parameter is a reference: a new
     * frame is pushed and the parameter gets a <em>copy of the argument's reference</em>
     * (Java passes arguments by value — for objects, the copied value is the handle).
     */
    public void call(String method, String param, String paramType, String argVar) {
        String id = top().vars.get(argVar).ref;
        Frame frame = new Frame(method);
        frame.vars.put(param, Var.reference(paramType, id));
        stack.add(frame);
        Trace.event("MEM_CALL",
                method + "(" + argVar + ") — new frame; " + param + " gets a COPY of the reference, "
                        + "so it points at the same object (" + id + ") as " + argVar,
                method + "(" + argVar + ") — новый кадр; " + param + " получает КОПИЮ ссылки, "
                        + "поэтому указывает на тот же объект (" + id + "), что и " + argVar,
                List.of("var:" + param, "obj:" + id), state());
    }

    /** Returns from the current method: the top frame and its slots disappear. */
    public void ret() {
        if (stack.size() > 1) {
            Frame gone = stack.remove(stack.size() - 1);
            Trace.event("MEM_RETURN",
                    gone.name + "() returns — its frame and local slots vanish; the heap object survives "
                            + "only if the caller still references it",
                    gone.name + "() возвращается — его кадр и локальные слоты исчезают; объект в куче выживает "
                            + "только если на него ещё ссылается вызывающий",
                    List.of(), state());
        }
    }

    // --- internals -------------------------------------------------------

    private Frame top() {
        return stack.get(stack.size() - 1);
    }

    private String allocate(String type, String... fields) {
        String id = "obj" + (++counter);
        Map<String, String> f = new LinkedHashMap<>();
        for (String pair : fields) {
            int eq = pair.indexOf('=');
            f.put(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
        }
        heap.put(id, new Obj(id, type, f));
        return id;
    }

    /** Ids reachable from any stack slot (everything else on the heap is garbage). */
    private Set<String> reachableIds() {
        Set<String> ids = new LinkedHashSet<>();
        for (Frame frame : stack) {
            for (Var v : frame.vars.values()) {
                if (v.ref != null) ids.add(v.ref);
            }
        }
        return ids;
    }

    /** Builds the JSON-serializable snapshot consumed by the visualizer. */
    private Object state() {
        Set<String> reachable = reachableIds();
        Map<String, Object> s = new LinkedHashMap<>();

        List<Object> frames = new ArrayList<>();
        for (Frame frame : stack) {
            Map<String, Object> fm = new LinkedHashMap<>();
            fm.put("name", frame.name);
            List<Object> vars = new ArrayList<>();
            for (Map.Entry<String, Var> e : frame.vars.entrySet()) {
                Var v = e.getValue();
                Map<String, Object> vm = new LinkedHashMap<>();
                vm.put("name", e.getKey());
                vm.put("kind", v.kind);
                vm.put("type", v.type);
                vm.put("value", v.value);
                vm.put("ref", v.ref);
                vars.add(vm);
            }
            fm.put("vars", vars);
            frames.add(fm);
        }
        s.put("frames", frames);

        List<Object> objects = new ArrayList<>();
        for (Obj o : heap.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", o.id);
            m.put("type", o.type);
            List<Object> fl = new ArrayList<>();
            for (Map.Entry<String, String> e : o.fields.entrySet()) {
                Map<String, Object> fm = new LinkedHashMap<>();
                fm.put("name", e.getKey());
                fm.put("value", e.getValue());
                fl.add(fm);
            }
            m.put("fields", fl);
            m.put("referenced", reachable.contains(o.id));
            objects.add(m);
        }
        s.put("heap", objects);
        return s;
    }

    private static final class Frame {
        final String name;
        final Map<String, Var> vars = new LinkedHashMap<>();

        Frame(String name) {
            this.name = name;
        }
    }

    private static final class Var {
        final String kind;   // "primitive" | "reference"
        final String type;   // declared type, e.g. "int" or "Point"
        String value;        // literal value for a primitive; null for a reference
        String ref;          // referenced object id for a reference; null otherwise

        private Var(String kind, String type, String value, String ref) {
            this.kind = kind;
            this.type = type;
            this.value = value;
            this.ref = ref;
        }

        static Var primitive(String type, String value) {
            return new Var("primitive", type, value, null);
        }

        static Var reference(String type, String ref) {
            return new Var("reference", type, null, ref);
        }
    }

    private static final class Obj {
        final String id;
        final String type;
        final Map<String, String> fields;

        Obj(String id, String type, Map<String, String> fields) {
            this.id = id;
            this.type = type;
            this.fields = fields;
        }
    }
}
