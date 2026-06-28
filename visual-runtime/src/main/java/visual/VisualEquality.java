package visual;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A teaching recorder for equals(Object) examples. User code owns the real
 * Point/Point3D implementations; this model records objects, comparison results
 * and contract checks as trace events for the frontend visualizer.
 */
public class VisualEquality {

    private final String scenario;
    private final List<VisualObject> objects = new ArrayList<>();
    private final List<Comparison> comparisons = new ArrayList<>();
    private final List<Check> checks = new ArrayList<>();
    private final List<Probe> probes = new ArrayList<>();

    public VisualEquality(String scenario) {
        this.scenario = scenario;
        Trace.event("EQUALITY_SCENARIO",
                "Started equality scenario '" + scenario + "'",
                "Начат сценарий равенства '" + scenario + "'",
                List.of(),
                state());
    }

    public void object(String id, Object instance, String label, String... fields) {
        String type = instance == null ? "null" : instance.getClass().getSimpleName();
        objects.add(new VisualObject(id, label, type, Arrays.asList(fields)));
        Trace.event("EQUALITY_OBJECTS",
                "Registered object " + id + " as " + type,
                "Зарегистрирован объект " + id + " как " + type,
                List.of("object:" + id),
                state());
    }

    public void compare(String leftId, String rightId, boolean result, String method) {
        recordComparison("EQUALITY_COMPARE", leftId, rightId, result, method,
                "Compared " + expression(leftId, rightId) + " using " + method
                        + " -> " + result,
                "Сравнили " + expression(leftId, rightId) + " через " + method
                        + " -> " + result);
    }

    public void compareWithGetClass(String leftId, String rightId, boolean result, String method) {
        recordComparison("EQUALITY_GETCLASS_GUARD", leftId, rightId, result, method,
                "getClass guard compared " + expression(leftId, rightId)
                        + " -> " + result,
                "Проверка getClass сравнила " + expression(leftId, rightId)
                        + " -> " + result);
    }

    public void compareWithCanEqual(String leftId, String rightId, boolean result, String method) {
        recordComparison("EQUALITY_CAN_EQUAL", leftId, rightId, result, method,
                "canEqual guard compared " + expression(leftId, rightId)
                        + " -> " + result,
                "Проверка canEqual сравнила " + expression(leftId, rightId)
                        + " -> " + result);
    }

    public void compareComposition(String leftId, String rightId, boolean result, String method) {
        recordComparison("EQUALITY_COMPOSITION", leftId, rightId, result, method,
                "Composition boundary compared " + expression(leftId, rightId)
                        + " -> " + result,
                "Граница композиции сравнила " + expression(leftId, rightId)
                        + " -> " + result);
    }

    public void checkSymmetry(String leftId, String rightId) {
        Comparison leftToRight = find(leftId, rightId);
        Comparison rightToLeft = find(rightId, leftId);
        boolean ok = leftToRight != null
                && rightToLeft != null
                && leftToRight.result == rightToLeft.result;
        String details = expression(leftId, rightId) + " = " + valueOf(leftToRight)
                + ", " + expression(rightId, leftId) + " = " + valueOf(rightToLeft);
        checks.add(new Check("symmetry", List.of(leftId, rightId), ok, details));
        Trace.event(ok ? "EQUALITY_SYMMETRY_OK" : "EQUALITY_SYMMETRY_BROKEN",
                (ok ? "Symmetry holds: " : "Symmetry broken: ") + details,
                (ok ? "Симметрия соблюдена: " : "Симметрия нарушена: ") + details,
                List.of("object:" + leftId, "object:" + rightId),
                state());
    }

    public void checkTransitivity(String firstId, String secondId, String thirdId) {
        Comparison firstToSecond = find(firstId, secondId);
        Comparison secondToThird = find(secondId, thirdId);
        Comparison firstToThird = find(firstId, thirdId);
        boolean broken = isTrue(firstToSecond) && isTrue(secondToThird) && !isTrue(firstToThird);
        boolean ok = !broken;
        String details = expression(firstId, secondId) + " = " + valueOf(firstToSecond)
                + ", " + expression(secondId, thirdId) + " = " + valueOf(secondToThird)
                + ", " + expression(firstId, thirdId) + " = " + valueOf(firstToThird);
        checks.add(new Check("transitivity", List.of(firstId, secondId, thirdId), ok, details));
        Trace.event(ok ? "EQUALITY_TRANSITIVITY_OK" : "EQUALITY_TRANSITIVITY_BROKEN",
                (ok ? "Transitivity holds: " : "Transitivity broken: ") + details,
                (ok ? "Транзитивность соблюдена: " : "Транзитивность нарушена: ") + details,
                List.of("object:" + firstId, "object:" + secondId, "object:" + thirdId),
                state());
    }

    public void collectionProbe(String expression, boolean result) {
        probes.add(new Probe(expression, result));
        Trace.event("EQUALITY_COLLECTION_SURPRISE",
                "Collection probe " + expression + " returned " + result,
                "Проверка коллекции " + expression + " вернула " + result,
                List.of("probe:" + expression),
                state());
    }

    private void recordComparison(String event, String leftId, String rightId,
                                  boolean result, String method, String descEn, String descRu) {
        Comparison comparison = new Comparison(leftId, rightId, result, method, event);
        comparisons.add(comparison);
        Trace.event(event, descEn, descRu,
                List.of("object:" + leftId, "object:" + rightId, "comparison:" + comparison.id()),
                state());
    }

    private Comparison find(String leftId, String rightId) {
        for (int i = comparisons.size() - 1; i >= 0; i--) {
            Comparison c = comparisons.get(i);
            if (c.leftId.equals(leftId) && c.rightId.equals(rightId)) {
                return c;
            }
        }
        return null;
    }

    private static boolean isTrue(Comparison comparison) {
        return comparison != null && comparison.result;
    }

    private static String valueOf(Comparison comparison) {
        return comparison == null ? "missing" : Boolean.toString(comparison.result);
    }

    private static String expression(String leftId, String rightId) {
        return leftId + ".equals(" + rightId + ")";
    }

    private Object state() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("scenario", scenario);
        s.put("objects", objects.stream().map(VisualObject::state).toList());
        s.put("comparisons", comparisons.stream().map(Comparison::state).toList());
        s.put("checks", checks.stream().map(Check::state).toList());
        s.put("probes", probes.stream().map(Probe::state).toList());
        return s;
    }

    private record VisualObject(String id, String label, String type, List<String> fields) {
        Map<String, Object> state() {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("id", id);
            s.put("label", label);
            s.put("type", type);
            s.put("fields", fields);
            return s;
        }
    }

    private record Comparison(String leftId, String rightId, boolean result, String method, String event) {
        String id() {
            return leftId + "->" + rightId;
        }

        Map<String, Object> state() {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("id", id());
            s.put("left", leftId);
            s.put("right", rightId);
            s.put("expression", expression(leftId, rightId));
            s.put("method", method);
            s.put("result", result);
            s.put("event", event);
            return s;
        }
    }

    private record Check(String kind, List<String> ids, boolean ok, String details) {
        Map<String, Object> state() {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("kind", kind);
            s.put("ids", ids);
            s.put("ok", ok);
            s.put("details", details);
            return s;
        }
    }

    private record Probe(String expression, boolean result) {
        Map<String, Object> state() {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("expression", expression);
            s.put("result", result);
            return s;
        }
    }
}
