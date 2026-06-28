package visual;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * A teaching wrapper that runs the same operation against HashSet,
 * LinkedHashSet and TreeSet. It keeps real JDK Set behavior while emitting a
 * compact comparison snapshot for the learning UI.
 *
 * @param <E> element type
 */
public class VisualSetComparison<E> {

    private final String name;
    private final Comparator<? super E> treeComparator;
    private final String treeOrderEn;
    private final String treeOrderRu;
    private final Set<E> hashSet = new HashSet<>();
    private final Set<E> linkedHashSet = new LinkedHashSet<>();
    private final NavigableSet<E> treeSet;

    public VisualSetComparison() {
        this("sets");
    }

    public VisualSetComparison(String name) {
        this(name, null, "natural ordering", "natural ordering");
    }

    public VisualSetComparison(String name, Comparator<? super E> treeComparator,
                               String treeOrderEn, String treeOrderRu) {
        this.name = name;
        this.treeComparator = treeComparator;
        this.treeOrderEn = blankToDefault(treeOrderEn, "custom Comparator");
        this.treeOrderRu = blankToDefault(treeOrderRu, this.treeOrderEn);
        this.treeSet = treeComparator == null ? new TreeSet<>() : new TreeSet<>(treeComparator);

        Trace.event("SETS_CREATED",
                "Created HashSet, LinkedHashSet and TreeSet comparison '" + name + "'",
                "Создано сравнение HashSet, LinkedHashSet и TreeSet '" + name + "'",
                List.of(),
                state("created", null, readyOutcomes(),
                        "All three implementations start empty, but they keep different iteration rules.",
                        "Все три реализации начинают пустыми, но сохраняют разные правила итерации."));
    }

    public void add(E value) {
        Outcome hash = addHash(value);
        Outcome linked = addLinked(value);
        Outcome tree = addTree(value);
        Map<String, Outcome> outcomes = outcomes(hash, linked, tree);

        String event = addEvent(value, hash, linked, tree);
        String valueText = show(value);
        String descEn = switch (event) {
            case "SET_DUPLICATE" -> "add(" + valueText
                    + ") changed nothing: every Set already had an equivalent element.";
            case "SET_TREESET_COMPARATOR_DUPLICATE" -> "add(" + valueText
                    + ") was accepted by hash-based sets, but TreeSet treated it as a duplicate by comparison.";
            case "SET_NULL_POLICY" -> "add(null) shows the null rule: hash-based sets can keep null, this TreeSet rejects it.";
            case "SET_TREESET_REJECTED" -> "add(" + valueText
                    + ") was rejected by TreeSet, while hash-based sets did not need ordering.";
            default -> "add(" + valueText
                    + ") runs through all three Sets; the stored values are unique but iteration order differs.";
        };
        String descRu = switch (event) {
            case "SET_DUPLICATE" -> "add(" + valueText
                    + ") ничего не изменил: в каждом Set уже был эквивалентный элемент.";
            case "SET_TREESET_COMPARATOR_DUPLICATE" -> "add(" + valueText
                    + ") принят хэш-реализациями, но TreeSet счёл его дубликатом по сравнению.";
            case "SET_NULL_POLICY" -> "add(null) показывает правило null: хэш-реализации могут хранить null, а этот TreeSet его отвергает.";
            case "SET_TREESET_REJECTED" -> "add(" + valueText
                    + ") отвергнут TreeSet, а хэш-реализациям порядок не нужен.";
            default -> "add(" + valueText
                    + ") проходит через все три Set; значения уникальны, но порядок итерации отличается.";
        };

        Trace.event(event, descEn, descRu, highlight(outcomes, valueText),
                state("add", valueText, outcomes,
                        "HashSet optimizes lookup, LinkedHashSet remembers insertion order, TreeSet keeps sorted order.",
                        "HashSet оптимизирует поиск, LinkedHashSet помнит порядок вставки, TreeSet держит сортировку."));
    }

    public void contains(E value) {
        Outcome hash = containsIn("HashSet", hashSet, value);
        Outcome linked = containsIn("LinkedHashSet", linkedHashSet, value);
        Outcome tree = containsIn("TreeSet", treeSet, value);
        Map<String, Outcome> outcomes = outcomes(hash, linked, tree);
        String valueText = show(value);

        Trace.event("SET_CONTAINS",
                "contains(" + valueText + ") checks membership; the lookup path differs by implementation.",
                "contains(" + valueText + ") проверяет наличие; путь поиска отличается по реализации.",
                highlight(outcomes, valueText),
                state("contains", valueText, outcomes,
                        "A Set answers membership, not position.",
                        "Set отвечает на вопрос о наличии, а не о позиции."));
    }

    public void remove(E value) {
        Outcome hash = removeFrom("HashSet", hashSet, value);
        Outcome linked = removeFrom("LinkedHashSet", linkedHashSet, value);
        Outcome tree = removeFrom("TreeSet", treeSet, value);
        Map<String, Outcome> outcomes = outcomes(hash, linked, tree);
        String valueText = show(value);

        Trace.event("SET_REMOVE",
                "remove(" + valueText + ") deletes an equivalent value when the implementation can find one.",
                "remove(" + valueText + ") удаляет эквивалентное значение, если реализация может его найти.",
                List.of("value:" + valueText),
                state("remove", valueText, outcomes,
                        "After removal, each remaining view still follows its own iteration rule.",
                        "После удаления каждое оставшееся представление всё равно следует своему правилу итерации."));
    }

    public void showIterationOrder() {
        Map<String, Outcome> outcomes = readyOutcomes();
        Trace.event("SET_ITERATION_ORDER",
                "Iteration order is the main visible difference: HashSet is unspecified, LinkedHashSet is insertion order, TreeSet is sorted.",
                "Порядок итерации — главное видимое отличие: у HashSet он не гарантирован, у LinkedHashSet — порядок вставки, у TreeSet — сортировка.",
                List.of("impl:hashset", "impl:linkedhashset", "impl:treeset"),
                state("iterate", null, outcomes,
                        "Use the implementation whose ordering promise matches the job.",
                        "Выбирайте реализацию, чьё обещание по порядку подходит задаче."));
    }

    public List<E> hashSetValues() {
        return new ArrayList<>(hashSet);
    }

    public List<E> linkedHashSetValues() {
        return new ArrayList<>(linkedHashSet);
    }

    public List<E> treeSetValues() {
        return new ArrayList<>(treeSet);
    }

    private Outcome addHash(E value) {
        E existing = findEqual(hashSet, value);
        boolean added = hashSet.add(value);
        return added
                ? new Outcome("added", "HashSet added the value using equals() and hashCode().",
                "HashSet добавил значение через equals() и hashCode().")
                : new Outcome("duplicate", "HashSet ignored a duplicate equal to " + show(existing) + ".",
                "HashSet проигнорировал дубликат, равный " + show(existing) + ".");
    }

    private Outcome addLinked(E value) {
        E existing = findEqual(linkedHashSet, value);
        boolean added = linkedHashSet.add(value);
        return added
                ? new Outcome("added", "LinkedHashSet added the value and linked it at the end.",
                "LinkedHashSet добавил значение и связал его в конце.")
                : new Outcome("duplicate", "LinkedHashSet ignored a duplicate; insertion order did not change.",
                "LinkedHashSet проигнорировал дубликат; порядок вставки не изменился.");
    }

    private Outcome addTree(E value) {
        E equivalent = findTreeEquivalent(value);
        try {
            boolean added = treeSet.add(value);
            if (added) {
                return new Outcome("added", "TreeSet inserted the value into sorted order.",
                        "TreeSet вставил значение в отсортированный порядок.");
            }
            return new Outcome("duplicate", "TreeSet ignored it because comparison matched " + show(equivalent) + ".",
                    "TreeSet проигнорировал его, потому что сравнение совпало с " + show(equivalent) + ".");
        } catch (RuntimeException ex) {
            return new Outcome("rejected", "TreeSet rejected the value: " + ex.getClass().getSimpleName() + ".",
                    "TreeSet отверг значение: " + ex.getClass().getSimpleName() + ".");
        }
    }

    private Outcome containsIn(String title, Set<E> set, E value) {
        try {
            boolean found = set.contains(value);
            return found
                    ? new Outcome("found", title + " found an equivalent value.",
                    title + " нашёл эквивалентное значение.")
                    : new Outcome("missing", title + " did not find the value.",
                    title + " не нашёл значение.");
        } catch (RuntimeException ex) {
            return new Outcome("rejected", title + " could not compare this value: "
                    + ex.getClass().getSimpleName() + ".", title + " не смог сравнить это значение: "
                    + ex.getClass().getSimpleName() + ".");
        }
    }

    private Outcome removeFrom(String title, Set<E> set, E value) {
        try {
            boolean removed = set.remove(value);
            return removed
                    ? new Outcome("removed", title + " removed an equivalent value.",
                    title + " удалил эквивалентное значение.")
                    : new Outcome("missing", title + " had nothing to remove.",
                    title + " не нашёл, что удалять.");
        } catch (RuntimeException ex) {
            return new Outcome("rejected", title + " could not remove this value: "
                    + ex.getClass().getSimpleName() + ".", title + " не смог удалить это значение: "
                    + ex.getClass().getSimpleName() + ".");
        }
    }

    private String addEvent(E value, Outcome hash, Outcome linked, Outcome tree) {
        if (value == null && "rejected".equals(tree.status)) {
            return "SET_NULL_POLICY";
        }
        if ("duplicate".equals(hash.status) && "duplicate".equals(linked.status)
                && "duplicate".equals(tree.status)) {
            return "SET_DUPLICATE";
        }
        if ("added".equals(hash.status) && "added".equals(linked.status)
                && "duplicate".equals(tree.status)) {
            return "SET_TREESET_COMPARATOR_DUPLICATE";
        }
        if ("rejected".equals(tree.status)) {
            return "SET_TREESET_REJECTED";
        }
        return "SET_ADD";
    }

    private Map<String, Outcome> readyOutcomes() {
        return outcomes(
                new Outcome("ready", "HashSet is ready for average O(1) membership checks.",
                        "HashSet готов к проверкам наличия в среднем за O(1)."),
                new Outcome("ready", "LinkedHashSet is ready to preserve insertion order.",
                        "LinkedHashSet готов сохранять порядок вставки."),
                new Outcome("ready", "TreeSet is ready to maintain " + treeOrderEn + ".",
                        "TreeSet готов поддерживать " + treeOrderRu + "."));
    }

    private Map<String, Outcome> outcomes(Outcome hash, Outcome linked, Outcome tree) {
        Map<String, Outcome> outcomes = new LinkedHashMap<>();
        outcomes.put("hashset", hash);
        outcomes.put("linkedhashset", linked);
        outcomes.put("treeset", tree);
        return outcomes;
    }

    private List<String> highlight(Map<String, Outcome> outcomes, String valueText) {
        List<String> tokens = new ArrayList<>();
        tokens.add("value:" + valueText);
        for (Map.Entry<String, Outcome> entry : outcomes.entrySet()) {
            Outcome outcome = entry.getValue();
            if ("added".equals(outcome.status) || "found".equals(outcome.status)
                    || "duplicate".equals(outcome.status) || "rejected".equals(outcome.status)) {
                tokens.add("impl:" + entry.getKey());
                tokens.add(entry.getKey() + ":value:" + valueText);
            }
        }
        return tokens;
    }

    private Object state(String operation, String probe, Map<String, Outcome> outcomes,
                         String noteEn, String noteRu) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("name", name);
        s.put("operation", operation);
        s.put("probe", probe);
        s.put("noteEn", noteEn);
        s.put("noteRu", noteRu);

        List<Object> implementations = new ArrayList<>();
        implementations.add(implState("hashset", "HashSet", "hash table",
                "хэш-таблица", "unspecified iteration order",
                "порядок итерации не гарантирован", "equals() + hashCode()",
                "equals() + hashCode()", "average O(1) add/contains",
                "в среднем O(1) для add/contains", hashSet, outcomes.get("hashset")));
        implementations.add(implState("linkedhashset", "LinkedHashSet",
                "HashSet plus insertion-order links", "HashSet плюс связи порядка вставки",
                "insertion order", "порядок вставки", "equals() + hashCode()",
                "equals() + hashCode()", "average O(1) with extra links",
                "в среднем O(1) с дополнительными связями", linkedHashSet,
                outcomes.get("linkedhashset")));
        implementations.add(implState("treeset", "TreeSet", "balanced tree",
                "сбалансированное дерево", treeOrderEn, treeOrderRu,
                "compareTo()/Comparator returns 0", "compareTo()/Comparator возвращает 0",
                "O(log n) add/contains", "O(log n) для add/contains",
                treeSet, outcomes.get("treeset")));
        s.put("implementations", implementations);
        return s;
    }

    private Object implState(String id, String title, String structureEn, String structureRu,
                             String orderEn, String orderRu, String uniquenessEn,
                             String uniquenessRu, String costEn, String costRu,
                             Set<E> values, Outcome outcome) {
        Map<String, Object> impl = new LinkedHashMap<>();
        impl.put("id", id);
        impl.put("title", title);
        impl.put("structureEn", structureEn);
        impl.put("structureRu", structureRu);
        impl.put("orderEn", orderEn);
        impl.put("orderRu", orderRu);
        impl.put("uniquenessEn", uniquenessEn);
        impl.put("uniquenessRu", uniquenessRu);
        impl.put("costEn", costEn);
        impl.put("costRu", costRu);
        impl.put("size", values.size());

        List<Object> serialized = new ArrayList<>();
        int index = 0;
        for (E value : values) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("index", index++);
            item.put("value", show(value));
            serialized.add(item);
        }
        impl.put("values", serialized);

        Map<String, Object> lastResult = new LinkedHashMap<>();
        lastResult.put("status", outcome.status);
        lastResult.put("detailEn", outcome.detailEn);
        lastResult.put("detailRu", outcome.detailRu);
        impl.put("lastResult", lastResult);
        return impl;
    }

    private E findEqual(Set<E> set, E value) {
        for (E candidate : set) {
            if (Objects.equals(candidate, value)) {
                return candidate;
            }
        }
        return null;
    }

    private E findTreeEquivalent(E value) {
        if (treeSet.isEmpty()) {
            return null;
        }
        for (E candidate : treeSet) {
            try {
                if (compare(candidate, value) == 0) {
                    return candidate;
                }
            } catch (RuntimeException ex) {
                return null;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private int compare(E left, E right) {
        if (treeComparator != null) {
            return treeComparator.compare(left, right);
        }
        return ((Comparable<? super E>) left).compareTo(right);
    }

    private static String show(Object value) {
        return value == null ? "null" : String.valueOf(value);
    }

    private static String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private record Outcome(String status, String detailEn, String detailRu) {
    }
}
