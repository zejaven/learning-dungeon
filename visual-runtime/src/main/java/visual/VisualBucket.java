package visual;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A <em>teaching model</em> of a single {@link java.util.HashMap} bucket. It
 * answers one interview question: <em>what data structure stores the values in a
 * bucket?</em>
 *
 * <p>By default a bucket is a singly-linked list of {@code Node} objects
 * (separate chaining). Since Java 8, once a single bucket grows to
 * {@link #TREEIFY_THRESHOLD} entries <em>and</em> the table capacity is at least
 * {@link #MIN_TREEIFY_CAPACITY}, that bucket is converted ("treeified") into a
 * red-black tree of {@code TreeNode} objects, turning the bucket's lookups from
 * O(n) into O(log n). It reverts to a list ("untreeifies") when the count drops
 * to {@link #UNTREEIFY_THRESHOLD}.
 *
 * <p>This is NOT the JDK implementation: every key handed to this model is
 * assumed to land in the same bucket (that is the whole point — a bucket only
 * treeifies after many collisions). The red-black colouring shown is
 * illustrative of a balanced tree, not the exact JDK rotation sequence.
 */
public class VisualBucket {

    /** A chain this long or longer is converted to a tree (JDK value). */
    static final int TREEIFY_THRESHOLD = 8;
    /** A tree this small or smaller is converted back to a list (JDK value). */
    static final int UNTREEIFY_THRESHOLD = 6;
    /** Below this table capacity the map resizes instead of treeifying (JDK value). */
    static final int MIN_TREEIFY_CAPACITY = 64;

    private final int index;
    private final int capacity;
    private final List<Node> nodes = new ArrayList<>();
    private boolean tree;

    public VisualBucket() {
        this(0, 64);
    }

    public VisualBucket(int index, int capacity) {
        this.index = index;
        this.capacity = capacity;
        Trace.event("BUCKET_CREATED",
                "Empty bucket " + index + " created (table capacity " + capacity
                        + "). It starts as a linked list.",
                "Создан пустой бакет " + index + " (ёмкость таблицы " + capacity
                        + "). Он начинается как связный список.",
                List.of("bucket:" + index), state());
    }

    /** Adds or updates a key. All keys are assumed to collide into this bucket. */
    public void add(String key, String value) {
        for (Node n : nodes) {
            if (n.key.equals(key)) {
                n.value = value;
                Trace.event(tree ? "TREE_ADD" : "LIST_ADD",
                        "Key " + key + " already present — value updated to " + value,
                        "Ключ " + key + " уже есть — значение обновлено на " + value,
                        List.of("node:" + key), state());
                return;
            }
        }

        Node node = new Node(key, value, key.hashCode());
        nodes.add(node);

        if (tree) {
            Trace.event("TREE_ADD",
                    "Inserted " + key + " into the red-black tree; it rebalances by hash.",
                    "Вставили " + key + " в красно-чёрное дерево; оно балансируется по hash.",
                    List.of("node:" + key), state());
        } else {
            Trace.event("LIST_ADD",
                    "Appended " + key + " to the chain (now " + nodes.size()
                            + " node(s) in this bucket).",
                    "Добавили " + key + " в конец цепочки (теперь " + nodes.size()
                            + " узл(ов) в этом бакете).",
                    List.of("node:" + key), state());
        }

        maybeTreeify();
    }

    private void maybeTreeify() {
        if (tree || nodes.size() < TREEIFY_THRESHOLD) {
            return;
        }
        if (capacity < MIN_TREEIFY_CAPACITY) {
            Trace.event("TREEIFY_SKIPPED",
                    "Chain reached " + nodes.size() + " but capacity " + capacity
                            + " < " + MIN_TREEIFY_CAPACITY
                            + " — the map resizes instead of treeifying.",
                    "Цепочка достигла " + nodes.size() + ", но ёмкость " + capacity
                            + " < " + MIN_TREEIFY_CAPACITY
                            + " — мапа делает resize вместо treeify.",
                    List.of("bucket:" + index), state());
            return;
        }
        tree = true;
        Trace.event("TREEIFY",
                "Chain reached " + TREEIFY_THRESHOLD + " entries and capacity >= "
                        + MIN_TREEIFY_CAPACITY + " — bucket converted to a red-black tree.",
                "Цепочка достигла " + TREEIFY_THRESHOLD + " записей и ёмкость >= "
                        + MIN_TREEIFY_CAPACITY + " — бакет превращён в красно-чёрное дерево.",
                List.of("bucket:" + index), state());
    }

    /** Looks a key up, counting the comparisons the structure costs. */
    public String get(String key) {
        if (tree) {
            List<Node> sorted = sortedByHash();
            int target = key.hashCode();
            int lo = 0, hi = sorted.size() - 1, compares = 0;
            while (lo <= hi) {
                int mid = (lo + hi) >>> 1;
                compares++;
                Node m = sorted.get(mid);
                if (m.hash == target && m.key.equals(key)) {
                    Trace.event("TREE_GET",
                            "Tree search found " + key + " in " + compares
                                    + " comparison(s) — O(log n).",
                            "Поиск в дереве нашёл " + key + " за " + compares
                                    + " сравнен(ий) — O(log n).",
                            List.of("node:" + key), state());
                    return m.value;
                }
                if (target < m.hash) hi = mid - 1; else lo = mid + 1;
            }
            Trace.event("TREE_GET",
                    "Tree search did not find " + key + " after " + compares
                            + " comparison(s) — returns null.",
                    "Поиск в дереве не нашёл " + key + " за " + compares
                            + " сравнен(ий) — вернёт null.",
                    List.of("bucket:" + index), state());
            return null;
        }

        int steps = 0;
        for (Node n : nodes) {
            steps++;
            if (n.key.equals(key)) {
                Trace.event("LIST_GET",
                        "Walked the chain " + steps + " step(s) to find " + key
                                + " — O(n).",
                        "Прошли по цепочке " + steps + " шаг(ов), чтобы найти " + key
                                + " — O(n).",
                        List.of("node:" + key), state());
                return n.value;
            }
        }
        Trace.event("LIST_GET",
                "Walked all " + steps + " node(s) and did not find " + key
                        + " — returns null.",
                "Прошли все " + steps + " узл(ов) и не нашли " + key
                        + " — вернёт null.",
                List.of("bucket:" + index), state());
        return null;
    }

    /** Removes a key; a small enough tree reverts to a linked list. */
    public void remove(String key) {
        boolean removed = false;
        for (Iterator<Node> it = nodes.iterator(); it.hasNext(); ) {
            if (it.next().key.equals(key)) {
                it.remove();
                removed = true;
                break;
            }
        }
        if (!removed) {
            return;
        }
        if (tree && nodes.size() <= UNTREEIFY_THRESHOLD) {
            tree = false;
            Trace.event("UNTREEIFY",
                    "Tree shrank to " + nodes.size() + " (<= " + UNTREEIFY_THRESHOLD
                            + ") — converted back to a linked list.",
                    "Дерево уменьшилось до " + nodes.size() + " (<= " + UNTREEIFY_THRESHOLD
                            + ") — снова превращено в связный список.",
                    List.of("bucket:" + index), state());
        } else {
            Trace.event(tree ? "TREE_ADD" : "LIST_ADD",
                    "Removed " + key + "; bucket now holds " + nodes.size() + " node(s).",
                    "Удалили " + key + "; в бакете теперь " + nodes.size() + " узл(ов).",
                    List.of("bucket:" + index), state());
        }
    }

    public int size() {
        return nodes.size();
    }

    private List<Node> sortedByHash() {
        List<Node> sorted = new ArrayList<>(nodes);
        sorted.sort(Comparator.comparingInt((Node n) -> n.hash));
        return sorted;
    }

    /** Builds the JSON-serializable snapshot consumed by the visualizer. */
    private Object state() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("index", index);
        s.put("capacity", capacity);
        s.put("structure", tree ? "tree" : "list");
        s.put("count", nodes.size());
        s.put("treeifyThreshold", TREEIFY_THRESHOLD);
        s.put("untreeifyThreshold", UNTREEIFY_THRESHOLD);
        s.put("minTreeifyCapacity", MIN_TREEIFY_CAPACITY);

        List<Object> list = new ArrayList<>();
        for (Node n : nodes) {
            list.add(nodeJson(n));
        }
        s.put("list", list);

        if (tree) {
            List<Node> sorted = sortedByHash();
            s.put("tree", buildTree(sorted, 0, sorted.size() - 1, 0));
        } else {
            s.put("tree", null);
        }
        return s;
    }

    /** Lays the sorted nodes out as a balanced binary tree for display. */
    private Object buildTree(List<Node> sorted, int lo, int hi, int depth) {
        if (lo > hi) {
            return null;
        }
        int mid = (lo + hi) >>> 1;
        Node n = sorted.get(mid);
        Map<String, Object> node = nodeJson(n);
        // Illustrative red-black colouring: root and even depths black, odd red.
        node.put("color", depth % 2 == 0 ? "B" : "R");
        node.put("left", buildTree(sorted, lo, mid - 1, depth + 1));
        node.put("right", buildTree(sorted, mid + 1, hi, depth + 1));
        return node;
    }

    private Map<String, Object> nodeJson(Node n) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", n.key);
        m.put("value", n.value);
        m.put("hash", n.hash);
        return m;
    }

    private static final class Node {
        final String key;
        String value;
        final int hash;

        Node(String key, String value, int hash) {
            this.key = key;
            this.value = value;
            this.hash = hash;
        }
    }
}
