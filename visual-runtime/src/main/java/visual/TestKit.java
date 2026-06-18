package visual;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Tiny helper for "challenge" topics: a hidden test harness calls
 * {@link #expect} for each case to report whether the learner's solution
 * produced the expected value. Each call emits a {@code TEST} trace event (via
 * {@link Trace}) carrying {@code name / passed / expected / actual}, so the
 * existing trace pipeline delivers the test results to the backend — no separate
 * protocol. Dependency-free, like the rest of visual-runtime.
 */
public final class TestKit {

    private TestKit() {
    }

    /** Reports one test case: passed iff {@code expected} deep-equals {@code actual}. */
    public static void expect(String name, Object expected, Object actual) {
        boolean passed = Objects.deepEquals(expected, actual);
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("name", name);
        state.put("passed", passed);
        state.put("expected", show(expected));
        state.put("actual", show(actual));
        Trace.event("TEST", name, name, List.of(), state);
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
