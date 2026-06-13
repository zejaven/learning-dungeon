package visual;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A <em>teaching model</em> of {@link java.util.HashMap}. It is NOT the JDK
 * implementation: it reproduces the key ideas an interviewer cares about —
 * hash spreading, bucket index, collisions (separate chaining), load factor
 * and resize — while emitting {@link Trace} events so the UI can visualize
 * every step.
 *
 * <p>Differences from the real HashMap are intentional and called out in the
 * topic's explanation.md (e.g. no red-black treeification of long chains).
 *
 * @param <K> key type
 * @param <V> value type
 */
public class VisualHashMap<K, V> {

    private static final int DEFAULT_CAPACITY = 16;
    private static final double LOAD_FACTOR = 0.75;

    private final String name;
    private List<Node<K, V>>[] buckets;
    private int capacity;
    private int threshold;
    private int size;

    public VisualHashMap() {
        this("map");
    }

    @SuppressWarnings("unchecked")
    public VisualHashMap(String name) {
        this.name = name;
        this.capacity = DEFAULT_CAPACITY;
        this.threshold = (int) (capacity * LOAD_FACTOR);
        this.buckets = (List<Node<K, V>>[]) new List[capacity];
        Trace.event("HASHMAP_CREATED",
                "Created '" + name + "' with capacity " + capacity
                        + " (threshold " + threshold + ")",
                "Создан '" + name + "' с ёмкостью " + capacity
                        + " (порог " + threshold + ")",
                List.of(), state());
    }

    /** Spread function mirroring HashMap: high bits influence the low bits. */
    static int spread(Object key) {
        if (key == null) return 0;
        int h = key.hashCode();
        return h ^ (h >>> 16);
    }

    private int indexFor(int spreadHash) {
        return (capacity - 1) & spreadHash;
    }

    public V put(K key, V value) {
        int hash = spread(key);
        int index = indexFor(hash);
        List<Node<K, V>> bucket = buckets[index];

        if (bucket == null) {
            bucket = new ArrayList<>();
            buckets[index] = bucket;
        }

        // Update existing key (by equals) in this bucket.
        for (Node<K, V> node : bucket) {
            if (eq(node.key, key)) {
                V old = node.value;
                node.value = value;
                Trace.event("HASHMAP_PUT",
                        "Key " + show(key) + " already present in bucket " + index
                                + " — value updated to " + show(value),
                        "Ключ " + show(key) + " уже есть в бакете " + index
                                + " — значение обновлено на " + show(value),
                        List.of("bucket:" + index, "node:" + show(key)),
                        state());
                return old;
            }
        }

        boolean collision = !bucket.isEmpty();
        bucket.add(new Node<>(key, value, hash));
        size++;

        if (collision) {
            Trace.event("HASHMAP_COLLISION",
                    "Collision: key " + show(key) + " hashes to bucket " + index
                            + ", which already holds " + (bucket.size() - 1)
                            + " entry(ies). Appended to the chain.",
                    "Коллизия: ключ " + show(key) + " попадает в бакет " + index
                            + ", где уже " + (bucket.size() - 1)
                            + " элемент(ов). Добавлен в цепочку.",
                    List.of("bucket:" + index, "node:" + show(key)),
                    state());
        } else {
            Trace.event("HASHMAP_PUT",
                    "Put " + show(key) + " into empty bucket " + index
                            + " (hash " + hash + " & " + (capacity - 1) + ")",
                    "Положили " + show(key) + " в пустой бакет " + index
                            + " (хэш " + hash + " & " + (capacity - 1) + ")",
                    List.of("bucket:" + index, "node:" + show(key)),
                    state());
        }

        if (size > threshold) {
            resize();
        }
        return null;
    }

    public V get(K key) {
        int hash = spread(key);
        int index = indexFor(hash);
        List<Node<K, V>> bucket = buckets[index];
        if (bucket != null) {
            for (Node<K, V> node : bucket) {
                if (eq(node.key, key)) {
                    Trace.event("HASHMAP_GET",
                            "get(" + show(key) + ") looked in bucket " + index
                                    + " and found value " + show(node.value),
                            "get(" + show(key) + ") заглянул в бакет " + index
                                    + " и нашёл значение " + show(node.value),
                            List.of("bucket:" + index, "node:" + show(key)),
                            state());
                    return node.value;
                }
            }
        }
        Trace.event("HASHMAP_GET",
                "get(" + show(key) + ") looked in bucket " + index
                        + " and found nothing — returns null",
                "get(" + show(key) + ") заглянул в бакет " + index
                        + " и ничего не нашёл — вернёт null",
                List.of("bucket:" + index),
                state());
        return null;
    }

    public int size() {
        return size;
    }

    @SuppressWarnings("unchecked")
    private void resize() {
        int oldCapacity = capacity;
        capacity = oldCapacity * 2;
        threshold = (int) (capacity * LOAD_FACTOR);
        List<Node<K, V>>[] old = buckets;
        buckets = (List<Node<K, V>>[]) new List[capacity];

        for (List<Node<K, V>> bucket : old) {
            if (bucket == null) continue;
            for (Node<K, V> node : bucket) {
                int index = indexFor(node.hash);
                List<Node<K, V>> target = buckets[index];
                if (target == null) {
                    target = new ArrayList<>();
                    buckets[index] = target;
                }
                target.add(node);
            }
        }

        Trace.event("HASHMAP_RESIZE",
                "Size " + size + " exceeded threshold — resized from "
                        + oldCapacity + " to " + capacity
                        + " buckets and rehashed all entries (new threshold "
                        + threshold + ")",
                "Размер " + size + " превысил порог — расширение с "
                        + oldCapacity + " до " + capacity
                        + " бакетов и перехэширование всех элементов (новый порог "
                        + threshold + ")",
                List.of(),
                state());
    }

    private static boolean eq(Object a, Object b) {
        return a == null ? b == null : a.equals(b);
    }

    private static String show(Object o) {
        return o == null ? "null" : o.toString();
    }

    /** Builds the JSON-serializable snapshot consumed by the visualizer. */
    private Object state() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("name", name);
        s.put("capacity", capacity);
        s.put("loadFactor", LOAD_FACTOR);
        s.put("threshold", threshold);
        s.put("size", size);

        List<Object> bucketList = new ArrayList<>(capacity);
        for (int i = 0; i < capacity; i++) {
            Map<String, Object> b = new LinkedHashMap<>();
            b.put("index", i);
            List<Object> nodes = new ArrayList<>();
            List<Node<K, V>> bucket = buckets[i];
            if (bucket != null) {
                for (Node<K, V> node : bucket) {
                    Map<String, Object> n = new LinkedHashMap<>();
                    n.put("key", show(node.key));
                    n.put("value", show(node.value));
                    n.put("hash", node.hash);
                    nodes.add(n);
                }
            }
            b.put("nodes", nodes);
            bucketList.add(b);
        }
        s.put("buckets", bucketList);
        return s;
    }

    private static final class Node<K, V> {
        final K key;
        V value;
        final int hash;

        Node(K key, V value, int hash) {
            this.key = key;
            this.value = value;
            this.hash = hash;
        }
    }
}
