package visual;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A <em>teaching model</em> of the <strong>{@code final} keyword in Java and the
 * contexts it can appear in</strong>. It is NOT a compiler: it reproduces the
 * mental model interviewers ask about — {@code final} means <em>assign exactly
 * once</em>, after which the <em>binding</em> (the name-to-value link) is locked.
 *
 * <p>{@code final} can appear on several things, and the model shows each as a
 * {@link Binding} with a {@code context}:
 * <ul>
 *   <li><b>local</b> — a local variable assigned once;</li>
 *   <li><b>static</b> — a {@code static final} constant;</li>
 *   <li><b>field</b> — an instance field, possibly a <em>blank final</em> that is
 *       declared without a value and must be assigned exactly once in the
 *       constructor;</li>
 *   <li><b>parameter</b> — a method parameter that cannot be reassigned in the body;</li>
 *   <li><b>method</b> — a method that cannot be overridden by a subclass;</li>
 *   <li><b>class</b> — a class that cannot be subclassed (e.g. {@code String}).</li>
 * </ul>
 *
 * <p>The single most important teaching point: on a <em>reference</em> variable,
 * {@code final} locks the <em>binding</em>, not the object. The handle can never
 * point elsewhere, but the object it points to may still be mutated. The model
 * makes that visible by letting a locked reference's contents change while the
 * binding stays put, and by rejecting any attempt to reassign a locked binding.
 */
public class VisualFinal {

    /** Declared bindings, in declaration order. */
    private final Map<String, Binding> bindings = new LinkedHashMap<>();
    /** Optional one-line note shown alongside the bindings (an assignment attempt or mutation). */
    private Note note;

    public VisualFinal() {
        Trace.event("FINAL_SCENE",
                "Empty scene. final means 'assign exactly once' — once a binding is set, it is locked.",
                "Пустая сцена. final значит «присвоить ровно один раз» — после установки связь блокируется.",
                List.of(), state());
    }

    /**
     * Declares a {@code final} local variable assigned at declaration, e.g.
     * {@code final int x = 10;}. The binding is locked immediately.
     */
    public void localVar(String name, String type, String value) {
        bindings.put(name, new Binding(name, "local", type, value, true, false));
        note = new Note("final " + type + " " + name + " = " + value + ";", "locked", null);
        Trace.event("FINAL_LOCAL",
                "final " + type + " " + name + " = " + value + "; — a final local variable: assigned once, the binding is now locked",
                "final " + type + " " + name + " = " + value + "; — final-локальная переменная: присвоена один раз, связь заблокирована",
                List.of("binding:" + name), state());
    }

    /**
     * Declares a {@code static final} constant, e.g.
     * {@code static final double PI = 3.14159;}. A class-level constant shared by
     * all instances, fixed once.
     */
    public void constant(String name, String type, String value) {
        bindings.put(name, new Binding(name, "static", type, value, true, false));
        note = new Note("static final " + type + " " + name + " = " + value + ";", "locked", null);
        Trace.event("FINAL_STATIC",
                "static final " + type + " " + name + " = " + value + "; — a constant: one shared value for the whole class, fixed once",
                "static final " + type + " " + name + " = " + value + "; — константа: одно общее значение на весь класс, задано один раз",
                List.of("binding:" + name), state());
    }

    /**
     * Declares a {@code final} reference variable that points to a <em>mutable</em>
     * object, e.g. {@code final List<String> list = new ArrayList<>();}. The binding
     * is locked, but the object behind it can still change — that is the key trap.
     */
    public void reference(String name, String type, String value) {
        bindings.put(name, new Binding(name, "local", type, value, true, true));
        note = new Note("final " + type + " " + name + " = " + value + ";", "locked", null);
        Trace.event("FINAL_LOCAL",
                "final " + type + " " + name + " = " + value + "; — a final reference: the handle is locked, but the object it points to may still be mutated",
                "final " + type + " " + name + " = " + value + "; — final-ссылка: указатель заблокирован, но объект, на который он указывает, ещё можно менять",
                List.of("binding:" + name), state());
    }

    /**
     * Declares a <em>blank final</em> field — {@code final} but not yet assigned,
     * e.g. {@code final int id;}. It is unlocked until the constructor assigns it
     * exactly once.
     */
    public void blankField(String name, String type) {
        bindings.put(name, new Binding(name, "field", type, "(unassigned)", false, false));
        note = new Note("final " + type + " " + name + ";", "assigned", null);
        Trace.event("FINAL_BLANK",
                "final " + type + " " + name + "; — a blank final field: declared without a value, still unlocked; the constructor must assign it exactly once",
                "final " + type + " " + name + "; — пустое final-поле: объявлено без значения, ещё не заблокировано; конструктор должен присвоить его ровно один раз",
                List.of("binding:" + name), state());
    }

    /**
     * Assigns a blank final field exactly once (as a constructor would). After this
     * the binding is locked.
     */
    public void assignOnce(String name, String value) {
        Binding b = bindings.get(name);
        b.value = value;
        b.locked = true;
        note = new Note("this." + name + " = " + value + ";", "assigned", null);
        Trace.event("FINAL_ASSIGN",
                "this." + name + " = " + value + "; — the blank final is assigned once in the constructor and is now locked",
                "this." + name + " = " + value + "; — пустое final-поле присваивается один раз в конструкторе и теперь заблокировано",
                List.of("binding:" + name), state());
    }

    /**
     * Attempts to reassign a locked {@code final} binding. The compiler rejects it;
     * the model leaves the binding unchanged and records the rejection.
     */
    public void reassignBlocked(String name, String attempted) {
        Binding b = bindings.get(name);
        note = new Note(name + " = " + attempted + ";", "blocked", b.value);
        Trace.event("FINAL_BLOCK",
                name + " = " + attempted + "; — rejected: cannot assign a value to a final variable; the binding stays " + b.value,
                name + " = " + attempted + "; — отклонено: нельзя присвоить значение final-переменной; связь остаётся " + b.value,
                List.of("binding:" + name), state());
    }

    /**
     * Mutates the object behind a {@code final} reference, e.g.
     * {@code list.add("b")}. Allowed: the binding (the handle) is unchanged while
     * the object's contents change.
     */
    public void mutateObject(String name, String call, String newContents) {
        Binding b = bindings.get(name);
        b.value = newContents;
        note = new Note(name + "." + call + ";", "mutated", newContents);
        Trace.event("FINAL_MUTATE",
                name + "." + call + "; — allowed: final locks the binding, not the object, so the object's contents become " + newContents,
                name + "." + call + "; — разрешено: final блокирует связь, а не объект, поэтому содержимое объекта становится " + newContents,
                List.of("binding:" + name), state());
    }

    /**
     * Declares a {@code final} method parameter, e.g. {@code void f(final int n)}.
     * It cannot be reassigned inside the method body.
     */
    public void parameter(String name, String type, String value) {
        bindings.put(name, new Binding(name, "parameter", type, value, true, false));
        note = new Note("void f(final " + type + " " + name + ")", "locked", null);
        Trace.event("FINAL_PARAM",
                "final " + type + " " + name + " — a final parameter: the method receives " + value + " and cannot reassign the parameter in its body",
                "final " + type + " " + name + " — final-параметр: метод получает " + value + " и не может переприсвоить параметр в теле",
                List.of("binding:" + name), state());
    }

    /**
     * Declares a {@code final} method — one a subclass cannot override.
     */
    public void method(String signature) {
        bindings.put(signature, new Binding(signature, "method", null, null, true, false));
        note = new Note("final " + signature, "locked", null);
        Trace.event("FINAL_METHOD",
                "final " + signature + " — a final method: subclasses inherit it but cannot override it",
                "final " + signature + " — final-метод: подклассы наследуют его, но не могут переопределить",
                List.of("binding:" + signature), state());
    }

    /**
     * Declares a {@code final} class — one that cannot be subclassed (e.g.
     * {@code String}).
     */
    public void clazz(String name) {
        bindings.put(name, new Binding(name, "class", null, null, true, false));
        note = new Note("final class " + name, "locked", null);
        Trace.event("FINAL_CLASS",
                "final class " + name + " — a final class: it cannot be extended, so no subclass can change its behaviour",
                "final class " + name + " — final-класс: его нельзя расширить, поэтому ни один подкласс не изменит его поведение",
                List.of("binding:" + name), state());
    }

    // --- internals -------------------------------------------------------

    /** Builds the JSON-serializable snapshot consumed by the visualizer. */
    private Object state() {
        Map<String, Object> s = new LinkedHashMap<>();
        List<Object> list = new ArrayList<>();
        for (Binding b : bindings.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", b.name);
            m.put("context", b.context);
            m.put("type", b.type);
            m.put("value", b.value);
            m.put("locked", b.locked);
            m.put("mutable", b.mutable);
            list.add(m);
        }
        s.put("bindings", list);
        if (note == null) {
            s.put("note", null);
        } else {
            Map<String, Object> n = new LinkedHashMap<>();
            n.put("expr", note.expr);
            n.put("status", note.status);
            n.put("detail", note.detail);
            s.put("note", n);
        }
        return s;
    }

    private static final class Binding {
        final String name;
        final String context;   // local | static | field | parameter | method | class
        final String type;      // declared type; null for method/class
        String value;           // current bound value/contents; null for method/class
        boolean locked;         // is the binding locked (assigned)?
        final boolean mutable;  // does it reference a mutable object whose contents can change?

        Binding(String name, String context, String type, String value, boolean locked, boolean mutable) {
            this.name = name;
            this.context = context;
            this.type = type;
            this.value = value;
            this.locked = locked;
            this.mutable = mutable;
        }
    }

    private record Note(String expr, String status, String detail) {
    }
}
