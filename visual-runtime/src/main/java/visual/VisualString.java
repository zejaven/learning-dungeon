package visual;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A <em>teaching model</em> of how Java {@link String}s live in memory. It is
 * NOT the JDK implementation: it reproduces the ideas an interviewer asks about
 * — immutability, the backing {@code byte[]} (a {@code char[]} before Java 9),
 * the string constant pool, {@code intern()}, reference vs. content equality,
 * and the mutable {@link StringBuilder} contrast — while emitting {@link Trace}
 * events so the UI can visualize every step.
 *
 * <p>The whole point: a {@code String} object never changes. Every operation
 * that looks like a "modification" ({@code concat}, {@code replace},
 * {@code toUpperCase}, …) allocates a <em>brand-new</em> {@code String} backed
 * by a <em>brand-new</em> array; the original object is left untouched and the
 * variable is simply re-pointed. A {@link StringBuilder}, by contrast, mutates
 * the <em>same</em> object in place.
 *
 * <p>The model keeps a tiny heap of objects, a set of named variables (the
 * "stack") and the string pool, so the visualizer can draw references pointing
 * at heap objects and fade objects that became garbage. Differences from the
 * real JVM are intentional and called out in the topic's explanation (e.g. no
 * real reachability graph or GC timing; compile-time literal folding is not
 * modelled).
 */
public class VisualString {

    /** Variable name -> heap object id it currently references (or null). */
    private final Map<String, String> vars = new LinkedHashMap<>();
    /** Object id -> heap object, in allocation order. */
    private final Map<String, Obj> heap = new LinkedHashMap<>();
    /** Pooled (interned) content -> the String object id that represents it. */
    private final Map<String, String> pool = new LinkedHashMap<>();

    private int counter;

    public VisualString() {
        Trace.event("STRING_SCENE",
                "Empty scene: no variables, no heap objects, empty string pool",
                "Пустая сцена: нет переменных, нет объектов в куче, пул строк пуст",
                List.of(), state());
    }

    /**
     * Assigns a string <em>literal</em> to a variable. Literals are interned, so
     * the same text always resolves to the same pooled object.
     */
    public void literal(String var, String text) {
        String id = pool.get(text);
        boolean reused = id != null;
        if (id == null) {
            id = allocateString(text);
            pool.put(text, id);
        }
        vars.put(var, id);
        Trace.event("STRING_LITERAL",
                var + " = \"" + text + "\" — literal " + (reused
                        ? "reused from the pool (same object as before)"
                        : "interned: stored once in the string pool"),
                var + " = \"" + text + "\" — литерал " + (reused
                        ? "переиспользован из пула (тот же объект, что и раньше)"
                        : "интернирован: один раз сохранён в пуле строк"),
                List.of("var:" + var, "obj:" + id), state());
    }

    /**
     * Assigns {@code new String(text)} to a variable: this forces a fresh heap
     * object that is distinct from the pooled literal of the same text.
     */
    public void newString(String var, String text) {
        String id = allocateString(text);
        vars.put(var, id);
        Trace.event("STRING_NEW",
                var + " = new String(\"" + text + "\") — a brand-new object on the heap, "
                        + "separate from the pooled literal",
                var + " = new String(\"" + text + "\") — новый объект в куче, "
                        + "отдельный от литерала из пула",
                List.of("var:" + var, "obj:" + id), state());
    }

    /** {@code var = var + suffix} — concatenation produces a NEW String. */
    public void concat(String var, String suffix) {
        String result = current(var) + suffix;
        derive(var, result,
                var + " = " + var + " + \"" + suffix + "\" — a NEW String \"" + result
                        + "\" is allocated; the original object is untouched",
                var + " = " + var + " + \"" + suffix + "\" — выделяется НОВАЯ строка \"" + result
                        + "\"; исходный объект не меняется");
    }

    /**
     * {@code var = var.replace(from, to)} — "changing a character" actually
     * builds a NEW String with a NEW backing array; nothing is mutated in place.
     */
    public void replace(String var, char from, char to) {
        String result = current(var).replace(from, to);
        derive(var, result,
                var + " = " + var + ".replace('" + from + "','" + to + "') — returns a NEW String "
                        + "with a NEW backing array; the original chars are never modified",
                var + " = " + var + ".replace('" + from + "','" + to + "') — возвращает НОВУЮ строку "
                        + "с НОВЫМ внутренним массивом; исходные символы не меняются");
    }

    /** {@code var = var.toUpperCase()} — also returns a NEW String. */
    public void toUpperCase(String var) {
        String result = current(var).toUpperCase();
        derive(var, result,
                var + " = " + var + ".toUpperCase() — returns a NEW String \"" + result
                        + "\"; the original stays as it was",
                var + " = " + var + ".toUpperCase() — возвращает НОВУЮ строку \"" + result
                        + "\"; исходная остаётся прежней");
    }

    /**
     * {@code var = var.intern()} — returns the canonical pooled instance for the
     * variable's current content (adding it to the pool if absent).
     */
    public void intern(String var) {
        String content = current(var);
        String pooledId = pool.get(content);
        boolean alreadyPooled = pooledId != null;
        if (!alreadyPooled) {
            pooledId = vars.get(var);
            pool.put(content, pooledId);
        }
        vars.put(var, pooledId);
        Trace.event("STRING_INTERN",
                var + ".intern() — " + (alreadyPooled
                        ? "the pool already holds \"" + content + "\", so the variable now points at that shared object"
                        : "\"" + content + "\" was not pooled yet, so this object is added to the pool"),
                var + ".intern() — " + (alreadyPooled
                        ? "в пуле уже есть \"" + content + "\", поэтому переменная теперь указывает на этот общий объект"
                        : "\"" + content + "\" ещё не было в пуле, поэтому этот объект добавлен в пул"),
                List.of("var:" + var, "obj:" + pooledId), state());
    }

    /**
     * Compares two variables both by reference ({@code ==}) and by content
     * ({@code equals}) and explains the difference.
     */
    public void compare(String a, String b) {
        String ia = vars.get(a);
        String ib = vars.get(b);
        boolean sameRef = Objects.equals(ia, ib);
        boolean sameContent = Objects.equals(current(a), current(b));
        List<String> hl = new ArrayList<>();
        hl.add("var:" + a);
        hl.add("var:" + b);
        if (ia != null) hl.add("obj:" + ia);
        if (ib != null) hl.add("obj:" + ib);
        Trace.event("STRING_COMPARE",
                a + " == " + b + " is " + sameRef + " (same object?), while "
                        + a + ".equals(" + b + ") is " + sameContent + " (same content?)",
                a + " == " + b + " равно " + sameRef + " (один и тот же объект?), а "
                        + a + ".equals(" + b + ") равно " + sameContent + " (одинаковое содержимое?)",
                hl, state());
    }

    /** {@code var = new StringBuilder(text)} — a MUTABLE buffer, for contrast. */
    public void builder(String var, String text) {
        String id = "sb" + (++counter);
        heap.put(id, new Obj(id, "StringBuilder", text, null));
        vars.put(var, id);
        Trace.event("BUILDER_NEW",
                var + " = new StringBuilder(\"" + text + "\") — a MUTABLE buffer; "
                        + "append() will change this very object",
                var + " = new StringBuilder(\"" + text + "\") — ИЗМЕНЯЕМЫЙ буфер; "
                        + "append() будет менять именно этот объект",
                List.of("var:" + var, "obj:" + id), state());
    }

    /** {@code var.append(suffix)} — mutates the SAME object in place. */
    public void append(String var, String suffix) {
        String id = vars.get(var);
        Obj o = heap.get(id);
        o.value = o.value + suffix;
        Trace.event("BUILDER_APPEND",
                var + ".append(\"" + suffix + "\") — mutates the SAME object (" + id
                        + ") in place to \"" + o.value + "\"; no new object is created",
                var + ".append(\"" + suffix + "\") — меняет ТОТ ЖЕ объект (" + id
                        + ") на месте на \"" + o.value + "\"; новый объект не создаётся",
                List.of("var:" + var, "obj:" + id), state());
    }

    /** Reads the current content a variable points at (empty if unassigned). */
    public String current(String var) {
        String id = vars.get(var);
        return id == null ? "" : heap.get(id).value;
    }

    // --- internals -------------------------------------------------------

    /** Shared body of concat/replace/toUpperCase: allocate a new String, repoint. */
    private void derive(String var, String result, String descEn, String descRu) {
        String id = allocateString(result);
        vars.put(var, id);
        Trace.event("STRING_DERIVE", descEn, descRu,
                List.of("var:" + var, "obj:" + id), state());
    }

    /** Allocates a String object together with its (final) backing byte[]. */
    private String allocateString(String text) {
        int n = ++counter;
        String arrId = "arr" + n;
        heap.put(arrId, new Obj(arrId, "byte[]", text, null));
        String strId = "str" + n;
        heap.put(strId, new Obj(strId, "String", text, arrId));
        return strId;
    }

    /** Builds the JSON-serializable snapshot consumed by the visualizer. */
    private Object state() {
        Set<String> reachable = reachableIds();
        Set<String> pooledIds = pooledIds();

        Map<String, Object> s = new LinkedHashMap<>();

        List<Object> variables = new ArrayList<>();
        for (Map.Entry<String, String> e : vars.entrySet()) {
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("name", e.getKey());
            v.put("ref", e.getValue());
            variables.add(v);
        }
        s.put("variables", variables);

        List<Object> objects = new ArrayList<>();
        for (Obj o : heap.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", o.id);
            m.put("type", o.type);
            m.put("value", o.value);
            m.put("backing", o.backing);
            m.put("pooled", pooledIds.contains(o.id));
            m.put("referenced", reachable.contains(o.id));
            objects.add(m);
        }
        s.put("objects", objects);

        s.put("pool", new ArrayList<Object>(pool.values()));
        return s;
    }

    /** Pool members plus the backing arrays they keep alive. */
    private Set<String> pooledIds() {
        Set<String> ids = new LinkedHashSet<>();
        for (String strId : pool.values()) {
            ids.add(strId);
            Obj o = heap.get(strId);
            if (o != null && o.backing != null) ids.add(o.backing);
        }
        return ids;
    }

    /** Objects reachable from a variable or from the pool (everything else is garbage). */
    private Set<String> reachableIds() {
        Set<String> roots = new LinkedHashSet<>();
        for (String id : vars.values()) {
            if (id != null) roots.add(id);
        }
        roots.addAll(pool.values());

        Set<String> reachable = new LinkedHashSet<>();
        for (String id : roots) {
            Obj o = heap.get(id);
            if (o == null) continue;
            reachable.add(o.id);
            if (o.backing != null) reachable.add(o.backing);
        }
        return reachable;
    }

    private static final class Obj {
        final String id;
        final String type;     // "String" | "byte[]" | "StringBuilder"
        String value;          // text content
        final String backing;  // backing byte[] id for a String; null otherwise

        Obj(String id, String type, String value, String backing) {
            this.id = id;
            this.type = type;
            this.value = value;
            this.backing = backing;
        }
    }
}
