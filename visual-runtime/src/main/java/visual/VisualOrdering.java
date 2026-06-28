package visual;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A teaching model for Java ordering. It traces the comparison result that
 * drives sorted collections and sorting algorithms: negative means left comes
 * first, positive means right comes first, and zero means the same sort slot.
 *
 * @param <T> element type
 */
public class VisualOrdering<T> {

    private final String name;
    private final Comparator<? super T> comparator;
    private final String orderDescriptionEn;
    private final String orderDescriptionRu;
    private final String source;
    private final List<T> values = new ArrayList<>();

    private Comparison lastComparison;
    private SafeCompare lastSafeCompare;
    private String operation = "created";

    public static <T extends Comparable<? super T>> VisualOrdering<T> natural(
            String name,
            String orderDescriptionEn,
            String orderDescriptionRu
    ) {
        return new VisualOrdering<>(name, null, orderDescriptionEn, orderDescriptionRu,
                "Comparable.compareTo()");
    }

    public static <T> VisualOrdering<T> usingComparator(
            String name,
            Comparator<? super T> comparator,
            String orderDescriptionEn,
            String orderDescriptionRu
    ) {
        return new VisualOrdering<>(name, Objects.requireNonNull(comparator),
                orderDescriptionEn, orderDescriptionRu, "Comparator.compare()");
    }

    private VisualOrdering(
            String name,
            Comparator<? super T> comparator,
            String orderDescriptionEn,
            String orderDescriptionRu,
            String source
    ) {
        this.name = name == null || name.isBlank() ? "values" : name;
        this.comparator = comparator;
        this.orderDescriptionEn = orderDescriptionEn == null || orderDescriptionEn.isBlank()
                ? "natural ordering"
                : orderDescriptionEn;
        this.orderDescriptionRu = orderDescriptionRu == null || orderDescriptionRu.isBlank()
                ? this.orderDescriptionEn
                : orderDescriptionRu;
        this.source = source;
        Trace.event("ORDERING_CREATED",
                "Created ordering trace '" + this.name + "' using " + source,
                "Создана трассировка порядка '" + this.name + "' через " + source,
                List.of(),
                state());
    }

    public VisualOrdering<T> add(T value) {
        values.add(value);
        operation = "add";
        Trace.event("ORDERING_VALUE_ADDED",
                "Added " + show(value) + " to the values that will be compared",
                "Добавили " + show(value) + " к значениям, которые будут сравниваться",
                List.of("value:" + show(value)),
                state());
        return this;
    }

    public int compare(T left, T right) {
        int result = doCompare(left, right);
        int sign = sign(result);
        operation = comparator == null ? "compareTo" : "comparator";
        lastComparison = new Comparison(source, show(left), show(right), result, sign);
        lastSafeCompare = null;

        String event = comparator == null ? "ORDERING_COMPARE_TO" : "ORDERING_COMPARATOR_COMPARE";
        Trace.event(event,
                source + " compared " + show(left) + " with " + show(right)
                        + " and returned " + result + ": " + meaningEn(sign),
                source + " сравнил " + show(left) + " с " + show(right)
                        + " и вернул " + result + ": " + meaningRu(sign),
                List.of("value:" + show(left), "value:" + show(right), "comparison"),
                state());

        if (sign == 0) {
            Trace.event("ORDERING_COMPARE_ZERO",
                    "Result 0 means both values occupy the same sort position",
                    "Результат 0 означает, что оба значения занимают одну позицию сортировки",
                    List.of("value:" + show(left), "value:" + show(right), "comparison"),
                    state());
        }
        return result;
    }

    public boolean sameSortPosition(T left, T right) {
        return compare(left, right) == 0;
    }

    public List<T> sort() {
        values.sort(this::compare);
        operation = "sorted";
        lastSafeCompare = null;
        Trace.event("ORDERING_SORTED",
                "Sorted " + values.size() + " value(s) using " + source,
                "Отсортировали " + values.size() + " значени(й) через " + source,
                List.of(),
                state());
        return values();
    }

    public List<T> values() {
        return new ArrayList<>(values);
    }

    public static int compareIntFields(
            String fieldName,
            String leftLabel,
            int leftValue,
            String rightLabel,
            int rightValue
    ) {
        int subtraction = leftValue - rightValue;
        int safeResult = Integer.compare(leftValue, rightValue);
        boolean overflowRisk = sign(subtraction) != sign(safeResult);
        Comparison comparison = new Comparison("Integer.compare()",
                leftLabel + "." + fieldName,
                rightLabel + "." + fieldName,
                safeResult,
                sign(safeResult));
        SafeCompare safeCompare = new SafeCompare(fieldName, leftLabel, leftValue,
                rightLabel, rightValue, subtraction, safeResult, overflowRisk);

        Trace.event("ORDERING_SAFE_COMPARE",
                "Integer.compare(" + leftValue + ", " + rightValue + ") returned "
                        + safeResult + "; subtraction returned " + subtraction
                        + (overflowRisk ? " with the wrong sign because of overflow" : ""),
                "Integer.compare(" + leftValue + ", " + rightValue + ") вернул "
                        + safeResult + "; вычитание вернуло " + subtraction
                        + (overflowRisk ? " с неправильным знаком из-за overflow" : ""),
                List.of("value:" + leftLabel, "value:" + rightLabel, "safeCompare"),
                staticState(fieldName, comparison, safeCompare));
        return safeResult;
    }

    @SuppressWarnings("unchecked")
    private int doCompare(T left, T right) {
        if (comparator != null) {
            return comparator.compare(left, right);
        }
        return ((Comparable<? super T>) left).compareTo(right);
    }

    private Object state() {
        Map<String, Object> s = baseState(name, orderDescriptionEn, orderDescriptionRu, source,
                operation, values);
        if (lastComparison != null) {
            s.put("lastComparison", lastComparison.toMap());
        }
        if (lastSafeCompare != null) {
            s.put("safeCompare", lastSafeCompare.toMap());
        }
        return s;
    }

    private static Object staticState(String fieldName, Comparison comparison, SafeCompare safeCompare) {
        List<Object> values = new ArrayList<>();
        values.add(valueState(0, safeCompare.leftLabel, safeCompare.leftValue));
        values.add(valueState(1, safeCompare.rightLabel, safeCompare.rightValue));

        Map<String, Object> s = baseState(fieldName, "numeric ascending order",
                "числовой порядок по возрастанию", "Integer.compare()", "safe-compare", List.of());
        s.put("values", values);
        s.put("lastComparison", comparison.toMap());
        s.put("safeCompare", safeCompare.toMap());
        return s;
    }

    private static Map<String, Object> baseState(
            String name,
            String orderEn,
            String orderRu,
            String source,
            String operation,
            List<?> rawValues
    ) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("name", name);
        s.put("order", orderEn);
        s.put("orderEn", orderEn);
        s.put("orderRu", orderRu);
        s.put("source", source);
        s.put("operation", operation);

        List<Object> serialized = new ArrayList<>();
        int index = 0;
        for (Object value : rawValues) {
            serialized.add(valueState(index++, show(value), null));
        }
        s.put("values", serialized);
        return s;
    }

    private static Map<String, Object> valueState(int index, String value, Integer numericValue) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("index", index);
        item.put("value", value);
        if (numericValue != null) {
            item.put("numericValue", numericValue);
        }
        return item;
    }

    private static String show(Object value) {
        return value == null ? "null" : String.valueOf(value);
    }

    private static int sign(int value) {
        return Integer.compare(value, 0);
    }

    private static String meaningEn(int sign) {
        if (sign < 0) {
            return "left comes before right";
        }
        if (sign > 0) {
            return "left comes after right";
        }
        return "same sort position";
    }

    private static String meaningRu(int sign) {
        if (sign < 0) {
            return "левое значение идёт раньше правого";
        }
        if (sign > 0) {
            return "левое значение идёт позже правого";
        }
        return "одна позиция сортировки";
    }

    private record Comparison(String source, String left, String right, int result, int sign) {
        Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("source", source);
            m.put("left", left);
            m.put("right", right);
            m.put("result", result);
            m.put("sign", sign);
            m.put("meaningEn", meaningEn(sign));
            m.put("meaningRu", meaningRu(sign));
            return m;
        }
    }

    private record SafeCompare(
            String fieldName,
            String leftLabel,
            int leftValue,
            String rightLabel,
            int rightValue,
            int subtractionResult,
            int safeResult,
            boolean overflowRisk
    ) {
        Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("fieldName", fieldName);
            m.put("leftLabel", leftLabel);
            m.put("leftValue", leftValue);
            m.put("rightLabel", rightLabel);
            m.put("rightValue", rightValue);
            m.put("subtractionResult", subtractionResult);
            m.put("safeResult", safeResult);
            m.put("overflowRisk", overflowRisk);
            return m;
        }
    }
}
