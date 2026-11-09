package visual;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Tiny helper for "challenge" topics: a hidden test harness calls
 * {@link #expect} for each case to report whether the learner's solution
 * produced the expected value. Each call emits a {@code TEST} trace event (via
 * {@link Trace}) carrying {@code name / passed / expected / actual}, so the
 * existing trace pipeline delivers the test results to the backend — no separate
 * protocol. Dependency-free, like the rest of visual-runtime.
 *
 * <p>Code tasks use {@link #run} instead, which keeps reporting when a case
 * blows up: it announces the case names ({@code TEST_PLAN}), isolates each case
 * so an exception or a hang fails only that case, and closes with
 * {@code TEST_END}. A run without {@code TEST_END} did not finish (say the
 * learner called {@code System.exit}), so it must not count as solved.
 */
public final class TestKit {

    /** Long enough for any honest case; the whole run is capped at a few seconds. */
    static final long CASE_TIMEOUT_MILLIS = 1000;

    private TestKit() {
    }

    /** One isolated case of {@link #run}: {@code body} yields the actual value. */
    public record Case(String name, Object expected, Callable<?> body) {
    }

    /** Declares a case for {@link #run}. */
    public static Case test(String name, Object expected, Callable<?> body) {
        return new Case(name, expected, body);
    }

    /** Runs the cases in order, each isolated from the others, and reports every one. */
    public static void run(Case... cases) {
        run(CASE_TIMEOUT_MILLIS, cases);
    }

    static void run(long timeoutMillis, Case... cases) {
        List<String> names = new ArrayList<>();
        for (Case c : cases) {
            names.add(c.name());
        }
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("names", names);
        emit("TEST_PLAN", plan);
        for (Case c : cases) {
            Outcome o = execute(c, timeoutMillis);
            emit("TEST", result(c.name(), o.passed(), o.expected(), o.actual()));
        }
        Map<String, Object> end = new LinkedHashMap<>();
        end.put("count", cases.length);
        emit("TEST_END", end);
    }

    /** Reports one test case: passed iff {@code expected} deep-equals {@code actual}. */
    public static void expect(String name, Object expected, Object actual) {
        boolean passed = Objects.deepEquals(expected, actual);
        Trace.event("TEST", name, name, List.of(), result(name, passed, show(expected), show(actual)));
    }

    private record Outcome(boolean passed, String expected, String actual) {
    }

    /**
     * The comparison and the rendering run on the worker too: they call the
     * learner's equals() and toString(), which can throw or hang as well.
     */
    private static Outcome execute(Case c, long timeoutMillis) {
        FutureTask<Outcome> task = new FutureTask<>(() -> {
            Object actual = c.body().call();
            return new Outcome(Objects.deepEquals(c.expected(), actual), show(c.expected()), show(actual));
        });
        Thread worker = new Thread(task, "test-case");
        worker.setDaemon(true); // a hung case must not keep the JVM alive
        worker.start();
        try {
            return task.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            return failed(c, "timed out after " + timeoutMillis + " ms");
        } catch (ExecutionException e) {
            return failed(c, "threw " + e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return failed(c, "interrupted");
        }
    }

    private static Outcome failed(Case c, String actual) {
        String expected;
        try {
            expected = show(c.expected());
        } catch (RuntimeException e) {
            expected = "?";
        }
        return new Outcome(false, expected, actual);
    }

    /**
     * Starts on a fresh line: the learner may have called print() without a
     * newline, and a trace line only counts from the start of a line.
     */
    private static void emit(String event, Map<String, Object> state) {
        System.out.println();
        Trace.event(event, event, event, List.of(), state);
    }

    private static Map<String, Object> result(String name, boolean passed, String expected, String actual) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("name", name);
        state.put("passed", passed);
        state.put("expected", expected);
        state.put("actual", actual);
        return state;
    }

    private static String show(Object o) {
        if (o == null) return "null";
        if (o instanceof int[] a) return Arrays.toString(a);
        if (o instanceof long[] a) return Arrays.toString(a);
        if (o instanceof double[] a) return Arrays.toString(a);
        if (o instanceof boolean[] a) return Arrays.toString(a);
        if (o instanceof char[] a) return Arrays.toString(a);
        if (o instanceof Object[] a) return Arrays.deepToString(a);
        return String.valueOf(o);
    }
}
