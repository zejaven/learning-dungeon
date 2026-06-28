package visual;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A teaching model for the equals/hashCode contract. It behaves like a tiny
 * HashSet: values are routed to a bucket by hashCode and then compared with
 * equals inside that bucket. The model also emits direct contract checks so a
 * topic can show why equal objects must have equal hash codes.
 *
 * @param <E> value type
 */
public class VisualEqualityContract<E> {

    private static final int DEFAULT_CAPACITY = 8;

    private final String name;
    private final List<Node<E>>[] buckets;
    private int nextId = 1;
    private int size;
    private Map<String, Object> lastOp;

    public VisualEqualityContract() {
        this("set");
    }

    @SuppressWarnings("unchecked")
    public VisualEqualityContract(String name) {
        this.name = name;
        this.buckets = (List<Node<E>>[]) new List[DEFAULT_CAPACITY];
        this.lastOp = op("created");
        Trace.event("EQUALITY_CREATED",
                "Created teaching set '" + name + "' with " + DEFAULT_CAPACITY + " buckets",
                "Создан учебный set '" + name + "' с " + DEFAULT_CAPACITY + " бакетами",
                List.of(),
                state());
    }

    /**
     * Compares two objects once and then emits a specific contract verdict when
     * they are equal.
     */
    public boolean compare(E left, E right) {
        boolean equal = Objects.equals(left, right);
        int leftHash = spread(left);
        int rightHash = spread(right);
        boolean hashesMatch = leftHash == rightHash;

        lastOp = op("compare");
        lastOp.put("left", show(left));
        lastOp.put("right", show(right));
        lastOp.put("equal", equal);
        lastOp.put("leftHash", leftHash);
        lastOp.put("rightHash", rightHash);
        lastOp.put("hashesMatch", hashesMatch);
        lastOp.put("result", equal ? (hashesMatch ? "contract-ok" : "contract-broken") : "not-equal");

        Trace.event("EQUALITY_COMPARE",
                "equals(" + show(left) + ", " + show(right) + ") returned " + equal
                        + "; hash codes are " + leftHash + " and " + rightHash,
                "equals(" + show(left) + ", " + show(right) + ") вернул " + equal
                        + "; hashCode равны " + leftHash + " и " + rightHash,
                List.of("object:left", "object:right"),
                state());

        if (equal && hashesMatch) {
            Trace.event("EQUALITY_CONTRACT_OK",
                    "Contract holds: equal objects have the same hashCode",
                    "Контракт соблюдён: равные объекты имеют одинаковый hashCode",
                    List.of("object:left", "object:right"),
                    state());
        } else if (equal) {
            Trace.event("EQUALITY_CONTRACT_BROKEN",
                    "Contract is broken: equals is true, but hashCode values differ",
                    "Контракт нарушен: equals вернул true, но hashCode различаются",
                    List.of("object:left", "object:right"),
                    state());
        }
        return equal;
    }

    /**
     * Checks the symmetry part of equals: a.equals(b) and b.equals(a) must agree.
     */
    public boolean checkSymmetry(E left, E right) {
        boolean leftRight = equalsOneWay(left, right);
        boolean rightLeft = equalsOneWay(right, left);
        boolean symmetric = leftRight == rightLeft;

        lastOp = op("symmetry");
        lastOp.put("left", show(left));
        lastOp.put("right", show(right));
        lastOp.put("leftEqualsRight", leftRight);
        lastOp.put("rightEqualsLeft", rightLeft);
        lastOp.put("result", symmetric ? "symmetric" : "symmetry-broken");

        Trace.event(symmetric ? "EQUALITY_SYMMETRY_OK" : "EQUALITY_SYMMETRY_BROKEN",
                "Symmetry check: left.equals(right) is " + leftRight
                        + ", right.equals(left) is " + rightLeft,
                "Проверка симметрии: left.equals(right) = " + leftRight
                        + ", right.equals(left) = " + rightLeft,
                List.of("object:left", "object:right"),
                state());
        return symmetric;
    }

    /**
     * Adds a value like HashSet: choose a bucket from hashCode, then use equals
     * only inside that bucket.
     */
    public boolean add(E value) {
        int hash = spread(value);
        int index = indexFor(hash);
        List<Node<E>> bucket = bucket(index);
        Node<E> duplicateInBucket = findEqual(bucket, value);

        if (duplicateInBucket != null) {
            lastOp = op("add-duplicate");
            lastOp.put("value", show(value));
            lastOp.put("hash", hash);
            lastOp.put("bucket", index);
            lastOp.put("result", "duplicate-rejected");
            Trace.event("EQUALITY_DUPLICATE_REJECTED",
                    "add(" + show(value) + ") found an equal value in bucket " + index
                            + " and did not add a duplicate",
                    "add(" + show(value) + ") нашёл равное значение в бакете " + index
                            + " и не добавил дубликат",
                    List.of("bucket:" + index, nodeToken(duplicateInBucket)),
                    state());
            return false;
        }

        Node<E> equalElsewhere = findEqualOutside(index, value);
        boolean collision = !bucket.isEmpty();
        Node<E> node = new Node<>(nextId++, value, hash, index);
        bucket.add(node);
        size++;

        lastOp = op("add");
        lastOp.put("value", show(value));
        lastOp.put("hash", hash);
        lastOp.put("bucket", index);
        lastOp.put("result", equalElsewhere == null ? "added" : "equal-in-another-bucket");

        if (equalElsewhere != null) {
            Trace.event("EQUALITY_DUPLICATE_STORED",
                    "add(" + show(value) + ") stored a second equal value because hashCode sent it to bucket "
                            + index + " instead of bucket " + equalElsewhere.bucketIndex,
                    "add(" + show(value) + ") сохранил второе равное значение, потому что hashCode отправил его в бакет "
                            + index + " вместо бакета " + equalElsewhere.bucketIndex,
                    List.of("bucket:" + index, "bucket:" + equalElsewhere.bucketIndex,
                            nodeToken(node), nodeToken(equalElsewhere)),
                    state());
        } else if (collision) {
            Trace.event("EQUALITY_HASH_COLLISION",
                    "add(" + show(value) + ") reached bucket " + index
                            + " where other non-equal values already live",
                    "add(" + show(value) + ") попал в бакет " + index
                            + ", где уже лежат другие неравные значения",
                    List.of("bucket:" + index, nodeToken(node)),
                    state());
        } else {
            Trace.event("EQUALITY_SET_ADD",
                    "add(" + show(value) + ") stored the value in empty bucket " + index,
                    "add(" + show(value) + ") сохранил значение в пустом бакете " + index,
                    List.of("bucket:" + index, nodeToken(node)),
                    state());
        }
        return true;
    }

    public boolean contains(E value) {
        int hash = spread(value);
        int index = indexFor(hash);
        Node<E> found = findEqual(bucket(index), value);

        lastOp = op("contains");
        lastOp.put("value", show(value));
        lastOp.put("hash", hash);
        lastOp.put("bucket", index);
        lastOp.put("result", found == null ? "missing" : "found");

        if (found != null) {
            Trace.event("EQUALITY_SET_CONTAINS",
                    "contains(" + show(value) + ") checked bucket " + index + " and found an equal value",
                    "contains(" + show(value) + ") проверил бакет " + index + " и нашёл равное значение",
                    List.of("bucket:" + index, nodeToken(found)),
                    state());
            return true;
        }

        Node<E> equalElsewhere = findEqualOutside(index, value);
        if (equalElsewhere != null) {
            lastOp.put("result", "equal-in-another-bucket");
            Trace.event("EQUALITY_LOOKUP_MISSED",
                    "contains(" + show(value) + ") checked bucket " + index
                            + ", but an equal value is stranded in bucket " + equalElsewhere.bucketIndex,
                    "contains(" + show(value) + ") проверил бакет " + index
                            + ", но равное значение осталось в бакете " + equalElsewhere.bucketIndex,
                    List.of("bucket:" + index, "bucket:" + equalElsewhere.bucketIndex, nodeToken(equalElsewhere)),
                    state());
        } else {
            Trace.event("EQUALITY_SET_MISS",
                    "contains(" + show(value) + ") checked bucket " + index + " and found nothing equal",
                    "contains(" + show(value) + ") проверил бакет " + index + " и не нашёл равного значения",
                    List.of("bucket:" + index),
                    state());
        }
        return false;
    }

    public int size() {
        return size;
    }

    static int spread(Object value) {
        if (value == null) return 0;
        int h = value.hashCode();
        return h ^ (h >>> 16);
    }

    private int indexFor(int hash) {
        return hash & (DEFAULT_CAPACITY - 1);
    }

    private List<Node<E>> bucket(int index) {
        List<Node<E>> bucket = buckets[index];
        if (bucket == null) {
            bucket = new ArrayList<>();
            buckets[index] = bucket;
        }
        return bucket;
    }

    private Node<E> findEqual(List<Node<E>> bucket, E value) {
        if (bucket == null) return null;
        for (Node<E> node : bucket) {
            if (Objects.equals(node.value, value)) {
                return node;
            }
        }
        return null;
    }

    private Node<E> findEqualOutside(int skipIndex, E value) {
        for (int i = 0; i < buckets.length; i++) {
            if (i == skipIndex || buckets[i] == null) continue;
            Node<E> found = findEqual(buckets[i], value);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static boolean equalsOneWay(Object left, Object right) {
        return left == null ? right == null : left.equals(right);
    }

    private Object state() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("name", name);
        s.put("capacity", DEFAULT_CAPACITY);
        s.put("size", size);
        s.put("lastOp", lastOp);

        List<Object> bucketList = new ArrayList<>(DEFAULT_CAPACITY);
        for (int i = 0; i < DEFAULT_CAPACITY; i++) {
            Map<String, Object> b = new LinkedHashMap<>();
            b.put("index", i);
            List<Object> nodes = new ArrayList<>();
            List<Node<E>> bucket = buckets[i];
            if (bucket != null) {
                for (Node<E> node : bucket) {
                    Map<String, Object> n = new LinkedHashMap<>();
                    n.put("id", "n" + node.id);
                    n.put("label", show(node.value));
                    n.put("storedHash", node.storedHash);
                    n.put("currentHash", spread(node.value));
                    n.put("storedBucket", node.bucketIndex);
                    nodes.add(n);
                }
            }
            b.put("nodes", nodes);
            bucketList.add(b);
        }
        s.put("buckets", bucketList);
        return s;
    }

    private static Map<String, Object> op(String kind) {
        Map<String, Object> op = new LinkedHashMap<>();
        op.put("kind", kind);
        return op;
    }

    private static String show(Object value) {
        return value == null ? "null" : String.valueOf(value);
    }

    private static String nodeToken(Node<?> node) {
        return "node:n" + node.id;
    }

    private static final class Node<E> {
        final int id;
        final E value;
        final int storedHash;
        final int bucketIndex;

        Node(int id, E value, int storedHash, int bucketIndex) {
            this.id = id;
            this.value = value;
            this.storedHash = storedHash;
            this.bucketIndex = bucketIndex;
        }
    }
}
