package visual;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A <em>teaching model</em> of how Java decides <em>which method runs</em> for a
 * call. It is NOT the real compiler/JVM: it reproduces, step by step, the two
 * mechanisms an interviewer contrasts — <strong>overloading</strong> and
 * <strong>overriding</strong> — while emitting {@link Trace} events so the UI can
 * visualize every decision.
 *
 * <p>The whole point is <em>when</em> and <em>by which type</em> the target is
 * chosen:
 * <ul>
 *   <li><strong>Overloading</strong> (same name, different parameter lists) is
 *       resolved at <em>compile time</em> by the <em>static</em> (declared) type
 *       of the arguments — "early binding".</li>
 *   <li><strong>Overriding</strong> (a subclass redefining an inherited method
 *       with the same signature) is resolved at <em>runtime</em> by the
 *       <em>actual</em> type of the object the reference points to — "late /
 *       dynamic binding" (virtual dispatch).</li>
 * </ul>
 *
 * <p>The model keeps a tiny class hierarchy and a set of named variables (each
 * with a static and a runtime type) and, on every call, records the candidate
 * methods, which one was chosen, and what drove the choice. {@code Object} is the
 * implicit root of every type, so every type is assignable to {@code Object}
 * without having to declare it.
 */
public class VisualDispatch {

    /** Class name -> its declaration (parent + the method signatures it declares). */
    private final Map<String, Cls> classes = new LinkedHashMap<>();
    /** Variable name -> its static (declared) and runtime (actual) type. */
    private final Map<String, Var> vars = new LinkedHashMap<>();

    /** The call currently being resolved (null until the first call). */
    private Call call;

    public VisualDispatch() {
        Trace.event("DISPATCH_SCENE",
                "Empty scene: no classes, no variables, no call resolved yet",
                "Пустая сцена: нет классов, нет переменных, вызов ещё не разрешён",
                List.of(), state());
    }

    /** Registers a class. {@code parent} may be null (then its root is Object). */
    public void declareClass(String name, String parent) {
        classes.put(name, new Cls(name, parent));
        Trace.event("DISPATCH_CLASS",
                "class " + name + (parent == null ? "" : " extends " + parent)
                        + " — added to the hierarchy",
                "class " + name + (parent == null ? "" : " extends " + parent)
                        + " — добавлен в иерархию",
                List.of("class:" + name), state());
    }

    /**
     * Declares a method on a class. Two methods with the same name but different
     * parameter lists form an <em>overload set</em>; a method whose signature
     * already exists on an ancestor is an <em>override</em>.
     */
    public void method(String className, String signature) {
        Cls c = classes.get(className);
        c.methods.add(signature);
        boolean overrides = ancestorDeclares(c.parent, signature);
        Trace.event("DISPATCH_METHOD",
                className + "." + signature + " — " + (overrides
                        ? "overrides the same signature inherited from a superclass"
                        : "declared on " + className),
                className + "." + signature + " — " + (overrides
                        ? "переопределяет ту же сигнатуру, унаследованную от суперкласса"
                        : "объявлен в " + className),
                List.of("class:" + className, "method:" + className + "#" + signature), state());
    }

    /**
     * Introduces a variable, e.g. {@code Animal a = new Dog()} is
     * {@code variable("a", "Animal", "Dog")}: static type {@code Animal}, runtime
     * type {@code Dog}.
     */
    public void variable(String name, String staticType, String runtimeType) {
        vars.put(name, new Var(name, staticType, runtimeType));
        Trace.event("DISPATCH_VAR",
                staticType + " " + name + " = new " + runtimeType + "() — static type "
                        + staticType + ", runtime type " + runtimeType,
                staticType + " " + name + " = new " + runtimeType + "() — статический тип "
                        + staticType + ", тип во время выполнения " + runtimeType,
                List.of("var:" + name), state());
    }

    /**
     * Resolves an <strong>overridden</strong> call {@code var.method()}. The
     * static type only gates whether the call compiles; the body that actually
     * runs is picked at runtime from the variable's <em>runtime</em> type, walking
     * up the inheritance chain to the most-derived class that declares it.
     */
    public void overrideCall(String varName, String methodName) {
        Var v = vars.get(varName);
        String signature = methodName + "()";
        List<Map<String, Object>> candidates = new ArrayList<>();
        String chosen = null;
        // Walk the runtime type's chain from most-derived up to the root.
        for (String t = v.runtimeType; t != null; t = parentOf(t)) {
            Cls c = classes.get(t);
            boolean declares = c != null && c.methods.contains(signature);
            Map<String, Object> cand = new LinkedHashMap<>();
            cand.put("owner", t);
            cand.put("signature", signature);
            cand.put("applicable", declares);
            cand.put("chosen", declares && chosen == null);
            candidates.add(cand);
            if (declares && chosen == null) chosen = t;
        }
        call = new Call(varName + "." + signature, "OVERRIDE", "RUNTIME",
                "runtime type = " + v.runtimeType, v.runtimeType, candidates,
                chosen + "." + signature);
        Trace.event("DISPATCH_OVERRIDE",
                varName + "." + signature + " — resolved at RUNTIME by the actual type ("
                        + v.runtimeType + "), so " + chosen + "." + signature + " runs"
                        + " (the static type " + v.staticType + " is ignored for the body)",
                varName + "." + signature + " — разрешается во ВРЕМЯ ВЫПОЛНЕНИЯ по фактическому типу ("
                        + v.runtimeType + "), поэтому выполняется " + chosen + "." + signature
                        + " (статический тип " + v.staticType + " не влияет на тело)",
                List.of("var:" + varName, "method:" + chosen + "#" + signature, "binding:RUNTIME"),
                state());
    }

    /**
     * Resolves an <strong>overloaded</strong> call {@code receiver.method(arg)}.
     * Among the overload set on {@code receiver}, the target is picked at compile
     * time by the <em>static</em> type of {@code argVar}: every overload whose
     * parameter type is a supertype of (or equal to) that static type is
     * applicable, and the most specific applicable one wins. The runtime type of
     * the argument is never consulted — this is the classic trap.
     */
    public void overloadCall(String receiver, String methodName, String argVar) {
        Var arg = vars.get(argVar);
        String staticType = arg.staticType;
        Cls c = classes.get(receiver);
        List<Map<String, Object>> candidates = new ArrayList<>();
        String bestParam = null;
        String bestSig = null;
        for (String sig : c.methods) {
            if (!sig.startsWith(methodName + "(")) continue;
            String param = paramType(sig);
            boolean applicable = isSubtype(staticType, param);
            // Most specific = parameter type that is a subtype of every other applicable one.
            if (applicable && (bestParam == null || isSubtype(param, bestParam))) {
                bestParam = param;
                bestSig = sig;
            }
            Map<String, Object> cand = new LinkedHashMap<>();
            cand.put("owner", receiver);
            cand.put("signature", sig);
            cand.put("applicable", applicable);
            cand.put("chosen", false);
            candidates.add(cand);
        }
        for (Map<String, Object> cand : candidates) {
            cand.put("chosen", cand.get("signature").equals(bestSig));
        }
        call = new Call(receiver + "." + methodName + "(" + argVar + ")", "OVERLOAD", "COMPILE",
                "static type of " + argVar + " = " + staticType, staticType, candidates,
                receiver + "." + bestSig);
        Trace.event("DISPATCH_OVERLOAD",
                receiver + "." + methodName + "(" + argVar + ") — resolved at COMPILE time by the static type of "
                        + argVar + " (" + staticType + "), so " + receiver + "." + bestSig
                        + " is chosen (runtime type " + arg.runtimeType + " is NOT consulted)",
                receiver + "." + methodName + "(" + argVar + ") — разрешается во ВРЕМЯ КОМПИЛЯЦИИ по статическому типу "
                        + argVar + " (" + staticType + "), поэтому выбирается " + receiver + "." + bestSig
                        + " (тип во время выполнения " + arg.runtimeType + " НЕ учитывается)",
                List.of("var:" + argVar, "method:" + receiver + "#" + bestSig, "binding:COMPILE"),
                state());
    }

    // --- internals -------------------------------------------------------

    private String parentOf(String type) {
        Cls c = classes.get(type);
        return c == null ? null : c.parent;
    }

    private boolean ancestorDeclares(String type, String signature) {
        for (String t = type; t != null; t = parentOf(t)) {
            Cls c = classes.get(t);
            if (c != null && c.methods.contains(signature)) return true;
        }
        return false;
    }

    /** Is {@code sub} assignable to {@code sup}? Object is the implicit root of all types. */
    private boolean isSubtype(String sub, String sup) {
        if (sup.equals("Object")) return true;
        for (String t = sub; t != null; t = parentOf(t)) {
            if (t.equals(sup)) return true;
        }
        return false;
    }

    /** Extracts the single parameter type from a signature, e.g. {@code print(String)} -> {@code String}. */
    private static String paramType(String signature) {
        int open = signature.indexOf('(');
        int close = signature.indexOf(')');
        return signature.substring(open + 1, close).trim();
    }

    /** Builds the JSON-serializable snapshot consumed by the visualizer. */
    private Object state() {
        Map<String, Object> s = new LinkedHashMap<>();

        List<Object> classList = new ArrayList<>();
        for (Cls c : classes.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", c.name);
            m.put("parent", c.parent);
            m.put("methods", new ArrayList<Object>(c.methods));
            classList.add(m);
        }
        s.put("classes", classList);

        List<Object> varList = new ArrayList<>();
        for (Var v : vars.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", v.name);
            m.put("staticType", v.staticType);
            m.put("runtimeType", v.runtimeType);
            varList.add(m);
        }
        s.put("variables", varList);

        if (call == null) {
            s.put("call", null);
        } else {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("site", call.site);
            m.put("kind", call.kind);
            m.put("binding", call.binding);
            m.put("basis", call.basis);
            m.put("basisType", call.basisType);
            m.put("candidates", call.candidates);
            m.put("result", call.result);
            s.put("call", m);
        }
        return s;
    }

    private static final class Cls {
        final String name;
        final String parent;
        final List<String> methods = new ArrayList<>();

        Cls(String name, String parent) {
            this.name = name;
            this.parent = parent;
        }
    }

    private static final class Var {
        final String name;
        final String staticType;
        final String runtimeType;

        Var(String name, String staticType, String runtimeType) {
            this.name = name;
            this.staticType = staticType;
            this.runtimeType = runtimeType;
        }
    }

    private static final class Call {
        final String site;
        final String kind;     // OVERRIDE | OVERLOAD
        final String binding;  // RUNTIME | COMPILE
        final String basis;    // human-readable reason text
        final String basisType;
        final List<Map<String, Object>> candidates;
        final String result;

        Call(String site, String kind, String binding, String basis, String basisType,
             List<Map<String, Object>> candidates, String result) {
            this.site = site;
            this.kind = kind;
            this.binding = binding;
            this.basis = basis;
            this.basisType = basisType;
            this.candidates = candidates;
            this.result = result;
        }
    }
}
