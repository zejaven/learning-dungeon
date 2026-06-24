package visual;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.TreeSet;

/**
 * A teaching wrapper around {@link java.util.TreeSet}. It keeps real TreeSet
 * semantics for sorted iteration, comparator-based uniqueness and navigable
 * queries, while emitting trace events for the learning UI.
 *
 * @param <E> element type
 */
public class VisualTreeSet<E> {

    private final String name;
    private final Comparator<? super E> comparator;
    private final String orderDescriptionEn;
    private final String orderDescriptionRu;
    private final NavigableSet<E> set;

    public VisualTreeSet() {
        this("set");
    }

    public VisualTreeSet(String name) {
        this(name, null, "natural ordering", "natural ordering");
    }

    public VisualTreeSet(String name, Comparator<? super E> comparator, String orderDescription) {
        this(name, comparator, orderDescription, localizeOrder(orderDescription));
    }

    public VisualTreeSet(String name, Comparator<? super E> comparator,
                         String orderDescriptionEn, String orderDescriptionRu) {
        this.name = name;
        this.comparator = comparator;
        this.orderDescriptionEn = orderDescriptionEn == null || orderDescriptionEn.isBlank()
                ? "custom comparator"
                : orderDescriptionEn;
        this.orderDescriptionRu = orderDescriptionRu == null || orderDescriptionRu.isBlank()
                ? this.orderDescriptionEn
                : orderDescriptionRu;
        this.set = comparator == null ? new TreeSet<>() : new TreeSet<>(comparator);
        Trace.event("TREESET_CREATED",
                "Created TreeSet '" + name + "' ordered by " + this.orderDescriptionEn,
                "Создан TreeSet '" + name + "' с порядком: " + this.orderDescriptionRu,
                List.of(),
                state("created", null, null, null));
    }

    /**
     * Adds a value. A duplicate means compareTo()/Comparator returned 0, even
     * when equals() would say the objects are different.
     */
    public boolean add(E value) {
        E equivalent = findEquivalent(value);
        boolean added = set.add(value);
        if (added) {
            Trace.event("TREESET_ADD",
                    "add(" + show(value) + ") inserted the value into sorted order",
                    "add(" + show(value) + ") вставил значение в отсортированный порядок",
                    List.of("value:" + show(value)),
                    state("add", show(value), "added", null));
        } else {
            String existing = show(equivalent);
            Trace.event("TREESET_DUPLICATE",
                    "add(" + show(value) + ") changed nothing: comparison matches existing value "
                            + existing,
                    "add(" + show(value) + ") ничего не изменил: сравнение совпало с уже существующим значением "
                            + existing,
                    List.of("value:" + existing, "probe:" + show(value)),
                    state("duplicate", show(value), existing, null));
        }
        return added;
    }

    public boolean contains(E value) {
        E equivalent = findEquivalent(value);
        boolean found = set.contains(value);
        Trace.event("TREESET_CONTAINS",
                "contains(" + show(value) + ") used the sorted tree and returned " + found,
                "contains(" + show(value) + ") использовал отсортированное дерево и вернул " + found,
                found && equivalent != null ? List.of("value:" + show(equivalent)) : List.of(),
                state("contains", show(value), String.valueOf(found), null));
        return found;
    }

    public boolean remove(E value) {
        E equivalent = findEquivalent(value);
        boolean removed = set.remove(value);
        Trace.event("TREESET_REMOVE",
                "remove(" + show(value) + ") returned " + removed,
                "remove(" + show(value) + ") вернул " + removed,
                removed && equivalent != null ? List.of("probe:" + show(equivalent)) : List.of(),
                state("remove", show(value), String.valueOf(removed), null));
        return removed;
    }

    public E lower(E value) {
        E result = set.lower(value);
        emitNavigate("lower", value, result);
        return result;
    }

    public E floor(E value) {
        E result = set.floor(value);
        emitNavigate("floor", value, result);
        return result;
    }

    public E ceiling(E value) {
        E result = set.ceiling(value);
        emitNavigate("ceiling", value, result);
        return result;
    }

    public E higher(E value) {
        E result = set.higher(value);
        emitNavigate("higher", value, result);
        return result;
    }

    public List<E> range(E fromInclusive, E toExclusive) {
        List<E> values = new ArrayList<>(set.subSet(fromInclusive, true, toExclusive, false));
        List<String> highlight = values.stream()
                .map(v -> "range:" + show(v))
                .toList();
        Trace.event("TREESET_RANGE",
                "range([" + show(fromInclusive) + ", " + show(toExclusive)
                        + ")) returned " + values.size() + " value(s) in sorted order",
                "range([" + show(fromInclusive) + ", " + show(toExclusive)
                        + ")) вернул " + values.size() + " значений в отсортированном порядке",
                highlight,
                state("range", show(fromInclusive) + ".." + show(toExclusive),
                        values.toString(), rangeState(fromInclusive, true, toExclusive, false, values)));
        return values;
    }

    public int size() {
        return set.size();
    }

    public List<E> values() {
        return new ArrayList<>(set);
    }

    private void emitNavigate(String operation, E probe, E result) {
        String resultText = show(result);
        Trace.event("TREESET_NAVIGATE",
                operation + "(" + show(probe) + ") returned " + resultText,
                operation + "(" + show(probe) + ") вернул " + resultText,
                result == null ? List.of() : List.of("value:" + resultText),
                state("navigate:" + operation, show(probe), resultText, null));
    }

    private E findEquivalent(E value) {
        for (E candidate : set) {
            if (compare(candidate, value) == 0) {
                return candidate;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private int compare(E left, E right) {
        if (comparator != null) {
            return comparator.compare(left, right);
        }
        return ((Comparable<? super E>) left).compareTo(right);
    }

    private Object state(String opKind, String probe, String result, Object range) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("name", name);
        s.put("order", orderDescriptionEn);
        s.put("orderEn", orderDescriptionEn);
        s.put("orderRu", orderDescriptionRu);
        s.put("size", set.size());

        List<Object> values = new ArrayList<>();
        int index = 0;
        for (E value : set) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("index", index++);
            item.put("value", show(value));
            values.add(item);
        }
        s.put("values", values);

        Map<String, Object> lastOp = new LinkedHashMap<>();
        lastOp.put("kind", opKind);
        lastOp.put("probe", probe);
        lastOp.put("result", result);
        s.put("lastOp", lastOp);
        if (range != null) {
            s.put("range", range);
        }
        return s;
    }

    private Object rangeState(E from, boolean fromInclusive, E to, boolean toInclusive, List<E> values) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("from", show(from));
        r.put("fromInclusive", fromInclusive);
        r.put("to", show(to));
        r.put("toInclusive", toInclusive);
        List<Object> serialized = new ArrayList<>();
        for (E value : values) {
            serialized.add(show(value));
        }
        r.put("values", serialized);
        return r;
    }

    private static String show(Object value) {
        return value == null ? "null" : String.valueOf(value);
    }

    private static String localizeOrder(String orderDescription) {
        if ("length Comparator".equals(orderDescription)) {
            return "Comparator по длине";
        }
        return orderDescription;
    }
}
