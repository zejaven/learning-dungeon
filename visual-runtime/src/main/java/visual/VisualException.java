package visual;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A <em>teaching model</em> of how Java exceptions are structured and how they
 * travel. It is NOT the JVM exception machinery: it reproduces the ideas an
 * interviewer asks about — the {@code Throwable} type hierarchy
 * ({@code Error} / checked {@code Exception} / unchecked {@code RuntimeException}),
 * how a thrown exception unwinds the call stack frame by frame until a matching
 * {@code catch} is found, how {@code finally} runs on the way out, and what an
 * "uncaught" exception means — while emitting {@link Trace} events so the UI can
 * visualize each step.
 *
 * <p>The model keeps an explicit call stack of {@link Frame}s. Each frame may
 * declare that its {@code try} block catches a given exception type (and its
 * subtypes) and whether it has a {@code finally} block. When
 * {@link #throwException} is called, the model searches the stack from the top
 * down: every frame that does not catch the exception is popped (running its
 * {@code finally} first if it has one) and the exception propagates to the
 * caller; the first frame that catches it handles it; if none do, the exception
 * is uncaught and the program would terminate.
 *
 * <p>A tiny built-in type table (a slice of the real {@code java.lang} /
 * {@code java.io} hierarchy) lets the model classify a type as
 * {@code ERROR} / {@code CHECKED} / {@code UNCHECKED} and decide whether a
 * {@code catch} of one type catches another. Differences from the real JDK are
 * intentional and called out in the topic's explanation (e.g. only common types
 * are known, and stack traces / suppressed exceptions are not modelled).
 */
public class VisualException {

    /** ERROR = not meant to be caught; CHECKED = compiler-enforced; UNCHECKED = RuntimeException. */
    private static final Map<String, String> PARENT = new LinkedHashMap<>();

    static {
        PARENT.put("Throwable", null);
        PARENT.put("Error", "Throwable");
        PARENT.put("OutOfMemoryError", "Error");
        PARENT.put("StackOverflowError", "Error");
        PARENT.put("Exception", "Throwable");
        PARENT.put("IOException", "Exception");
        PARENT.put("FileNotFoundException", "IOException");
        PARENT.put("SQLException", "Exception");
        PARENT.put("InterruptedException", "Exception");
        PARENT.put("RuntimeException", "Exception");
        PARENT.put("NullPointerException", "RuntimeException");
        PARENT.put("IllegalArgumentException", "RuntimeException");
        PARENT.put("NumberFormatException", "IllegalArgumentException");
        PARENT.put("IllegalStateException", "RuntimeException");
        PARENT.put("ArithmeticException", "RuntimeException");
        PARENT.put("IndexOutOfBoundsException", "RuntimeException");
        PARENT.put("ArrayIndexOutOfBoundsException", "IndexOutOfBoundsException");
        PARENT.put("ClassCastException", "RuntimeException");
    }

    private final List<Frame> stack = new ArrayList<>();
    private Thrown exception;

    public VisualException() {
        Trace.event("EXCEPTION_SCENE",
                "Empty scene: no call stack, no exception in flight",
                "Пустая сцена: стек вызовов пуст, исключений в полёте нет",
                List.of(), state());
    }

    /** Pushes a plain frame (a method with no try/catch and no finally). */
    public void call(String method) {
        call(method, null, false);
    }

    /**
     * Pushes a frame onto the call stack.
     *
     * @param method    the method name shown in the frame
     * @param catchType the exception type this frame's {@code try} block catches
     *                  (and its subtypes), or {@code null}/empty for no {@code catch}
     * @param hasFinally whether this frame has a {@code finally} block
     */
    public void call(String method, String catchType, boolean hasFinally) {
        String catches = (catchType == null || catchType.isEmpty()) ? null : catchType;
        stack.add(new Frame(method, catches, hasFinally));
        String guard = catches != null
                ? " (try/catch " + catches + (hasFinally ? " + finally" : "") + ")"
                : (hasFinally ? " (try/finally)" : "");
        Trace.event("STACK_PUSH",
                "Call " + method + "()" + guard + " — a new frame is pushed on the call stack",
                "Вызов " + method + "()" + guard + " — на стек вызовов кладётся новый фрейм",
                List.of("frame:" + (stack.size() - 1)), state());
    }

    /**
     * Throws an exception from the current (top) frame and lets it propagate.
     * Frames that do not catch it are unwound (running {@code finally} first);
     * the first matching {@code catch} handles it; otherwise it is uncaught.
     */
    public void throwException(String type, String message) {
        String category = category(type);
        exception = new Thrown(type, message, category, "in-flight");
        Trace.event("THROW",
                "throw new " + type + "(\"" + message + "\") — " + categoryWord(category, true)
                        + "; the JVM now looks for a matching catch up the stack",
                "throw new " + type + "(\"" + message + "\") — " + categoryWord(category, false)
                        + "; JVM ищет подходящий catch вверх по стеку",
                topHighlight("exception"), state());

        while (true) {
            if (stack.isEmpty()) {
                exception = exception.withStatus("uncaught");
                Trace.event("UNCAUGHT",
                        type + " reached the top of the stack uncaught — the thread terminates "
                                + "and the JVM prints the stack trace",
                        type + " дошло до вершины стека и не поймано — поток завершается, "
                                + "JVM печатает stack trace",
                        List.of("exception"), state());
                return;
            }
            Frame top = stack.get(stack.size() - 1);
            if (top.catches != null && assignable(type, top.catches)) {
                exception = exception.withStatus("caught");
                Trace.event("CATCH",
                        top.method + "() catches " + top.catches + " — it matches " + type
                                + ", so the exception is handled here and propagation stops",
                        top.method + "() ловит " + top.catches + " — он подходит для " + type
                                + ", исключение обработано здесь, распространение прекращается",
                        topHighlight("exception"), state());
                if (top.hasFinally) {
                    Trace.event("FINALLY",
                            "finally in " + top.method + "() runs after the catch — cleanup always executes",
                            "finally в " + top.method + "() выполняется после catch — очистка происходит всегда",
                            topHighlight(), state());
                }
                return;
            }
            // This frame does not handle it: finally still runs, then we unwind.
            if (top.hasFinally) {
                Trace.event("FINALLY",
                        "finally in " + top.method + "() runs while unwinding — it executes even though "
                                + top.method + "() does not catch the exception",
                        "finally в " + top.method + "() выполняется при раскрутке стека — он работает, "
                                + "хотя " + top.method + "() не ловит исключение",
                        topHighlight(), state());
            }
            stack.remove(stack.size() - 1);
            Trace.event("PROPAGATE",
                    top.method + "() did not catch " + type + " — its frame is popped and the exception "
                            + "propagates to the caller",
                    top.method + "() не поймал " + type + " — его фрейм снимается, исключение "
                            + "распространяется к вызывающему методу",
                    topHighlight("exception"), state());
        }
    }

    /**
     * Classifies a type without throwing it (a "tour" of the hierarchy): shows
     * where it sits — {@code Error}, checked {@code Exception}, or unchecked
     * {@code RuntimeException}.
     */
    public void describe(String type) {
        String category = category(type);
        exception = new Thrown(type, "", category, "info");
        Trace.event("TYPE_INFO",
                type + " is " + categoryWord(category, true) + " (chain: " + chain(type) + ")",
                type + " — это " + categoryWord(category, false) + " (цепочка: " + chain(type) + ")",
                List.of("exception"), state());
    }

    // --- classification helpers -----------------------------------------

    /** ERROR / CHECKED / UNCHECKED for a known type (UNKNOWN treated as checked). */
    static String category(String type) {
        String t = type;
        while (t != null) {
            if (t.equals("Error")) return "ERROR";
            if (t.equals("RuntimeException")) return "UNCHECKED";
            if (t.equals("Exception")) return "CHECKED";
            t = PARENT.get(t);
        }
        return "CHECKED";
    }

    /** True if a {@code catch (catchType)} would catch a thrown {@code thrownType}. */
    static boolean assignable(String thrownType, String catchType) {
        String t = thrownType;
        while (t != null) {
            if (t.equals(catchType)) return true;
            t = PARENT.get(t);
        }
        return false;
    }

    /** Human-readable ancestor chain up to Throwable, e.g. "NPE -> RuntimeException -> Exception". */
    private static String chain(String type) {
        StringBuilder sb = new StringBuilder();
        String t = type;
        while (t != null) {
            if (sb.length() > 0) sb.append(" -> ");
            sb.append(t);
            t = PARENT.get(t);
        }
        return sb.toString();
    }

    private static String categoryWord(String category, boolean en) {
        return switch (category) {
            case "ERROR" -> en ? "an Error (serious; not meant to be caught)"
                    : "Error (серьёзная проблема; ловить не принято)";
            case "UNCHECKED" -> en ? "an unchecked RuntimeException (no compiler check)"
                    : "непроверяемое RuntimeException (компилятор не требует обработки)";
            default -> en ? "a checked Exception (the compiler forces you to handle or declare it)"
                    : "проверяемое Exception (компилятор заставляет обработать или объявить)";
        };
    }

    // --- state ----------------------------------------------------------

    private List<String> topHighlight(String... extra) {
        List<String> hl = new ArrayList<>();
        if (!stack.isEmpty()) hl.add("frame:" + (stack.size() - 1));
        for (String e : extra) hl.add(e);
        return hl;
    }

    private Object state() {
        Map<String, Object> s = new LinkedHashMap<>();
        List<Object> frames = new ArrayList<>();
        for (Frame f : stack) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("method", f.method);
            m.put("catches", f.catches);
            m.put("hasFinally", f.hasFinally);
            frames.add(m);
        }
        s.put("stack", frames);
        if (exception == null) {
            s.put("exception", null);
        } else {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("type", exception.type);
            e.put("message", exception.message);
            e.put("category", exception.category);
            e.put("status", exception.status);
            s.put("exception", e);
        }
        return s;
    }

    private static final class Frame {
        final String method;
        final String catches;   // exception type this frame catches, or null
        final boolean hasFinally;

        Frame(String method, String catches, boolean hasFinally) {
            this.method = method;
            this.catches = catches;
            this.hasFinally = hasFinally;
        }
    }

    private static final class Thrown {
        final String type;
        final String message;
        final String category;   // ERROR | CHECKED | UNCHECKED
        final String status;     // in-flight | caught | uncaught | info

        Thrown(String type, String message, String category, String status) {
            this.type = type;
            this.message = message;
            this.category = category;
            this.status = status;
        }

        Thrown withStatus(String s) {
            return new Thrown(type, message, category, s);
        }
    }
}
