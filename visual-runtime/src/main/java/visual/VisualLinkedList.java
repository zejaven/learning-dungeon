package visual;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A <em>teaching model</em> of {@link java.util.LinkedList}. It is NOT the JDK
 * implementation, but it reproduces what an interviewer asks about: a
 * doubly-linked chain of nodes with a head and a tail, O(1) add/remove at the
 * ends, and O(n) access by index because there is no array to jump into — you
 * must walk node by node.
 *
 * <p>Like the JDK, {@code get(index)} walks from whichever end is closer, and
 * the emitted {@code state} reports the number of <em>hops</em> taken, which is
 * exactly the cost ArrayList avoids with its backing array.
 *
 * @param <E> element type
 */
public class VisualLinkedList<E> {

    private final String name;
    private Node<E> head;
    private Node<E> tail;
    private int size;

    public VisualLinkedList() {
        this("list");
    }

    public VisualLinkedList(String name) {
        this.name = name;
        Trace.event("LINKEDLIST_CREATED",
                "Created an empty LinkedList '" + name + "' (head and tail are null)",
                "Создан пустой LinkedList '" + name + "' (head и tail равны null)",
                List.of(), state(0, "none"));
    }

    /** Appends at the tail: O(1) — just relink, no walking. */
    public void addLast(E value) {
        Node<E> node = new Node<>(value);
        if (tail == null) {
            head = tail = node;
        } else {
            tail.next = node;
            node.prev = tail;
            tail = node;
        }
        size++;
        Trace.event("LINKEDLIST_ADD",
                "addLast(" + show(value) + ") relinked the tail — O(1), no walking",
                "addLast(" + show(value) + ") перецепил tail — O(1), без обхода",
                List.of("node:" + (size - 1)), state(0, "none"));
    }

    /** Convenience alias for {@link #addLast(Object)}. */
    public void add(E value) {
        addLast(value);
    }

    /** Prepends at the head: O(1) — just relink, no walking. */
    public void addFirst(E value) {
        Node<E> node = new Node<>(value);
        if (head == null) {
            head = tail = node;
        } else {
            node.next = head;
            head.prev = node;
            head = node;
        }
        size++;
        Trace.event("LINKEDLIST_ADD",
                "addFirst(" + show(value) + ") relinked the head — O(1), no walking",
                "addFirst(" + show(value) + ") перецепил head — O(1), без обхода",
                List.of("node:0"), state(0, "none"));
    }

    /** Random access by index: O(n) — walk from the nearer end, counting hops. */
    public E get(int index) {
        checkIndex(index);
        int hops = 0;
        Node<E> node;
        boolean fromHead = index <= size / 2;
        if (fromHead) {
            node = head;
            for (int i = 0; i < index; i++) {
                node = node.next;
                hops++;
            }
        } else {
            node = tail;
            for (int i = size - 1; i > index; i--) {
                node = node.prev;
                hops++;
            }
        }
        String end = fromHead ? "head" : "tail";
        Trace.event("LINKEDLIST_GET",
                "get(" + index + ") walked " + hops + " hop(s) from the " + end
                        + " to reach " + show(node.value) + " — O(n)",
                "get(" + index + ") прошёл " + hops + " шаг(ов) от " + end
                        + ", чтобы добраться до " + show(node.value) + " — O(n)",
                List.of("node:" + index), state(hops, "hops"));
        return node.value;
    }

    /**
     * Inserts at {@code index}: O(n) to walk to the position, then O(1) to
     * relink. No elements are shifted or copied.
     */
    public void add(int index, E value) {
        if (index < 0 || index > size) {
            throw new IndexOutOfBoundsException("index " + index + ", size " + size);
        }
        if (index == size) {
            addLast(value);
            return;
        }
        if (index == 0) {
            addFirst(value);
            return;
        }
        int hops = 0;
        Node<E> succ = head;
        for (int i = 0; i < index; i++) {
            succ = succ.next;
            hops++;
        }
        Node<E> pred = succ.prev;
        Node<E> node = new Node<>(value);
        node.prev = pred;
        node.next = succ;
        pred.next = node;
        succ.prev = node;
        size++;
        Trace.event("LINKEDLIST_INSERT",
                "add(" + index + ", " + show(value) + ") walked " + hops
                        + " hop(s), then relinked two pointers — O(n) walk, O(1) link",
                "add(" + index + ", " + show(value) + ") прошёл " + hops
                        + " шаг(ов), затем перецепил два указателя — O(n) обход, O(1) вставка",
                List.of("node:" + index), state(hops, "hops"));
    }

    public int size() {
        return size;
    }

    private void checkIndex(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("index " + index + ", size " + size);
        }
    }

    private static String show(Object o) {
        return o == null ? "null" : o.toString();
    }

    /** Builds the JSON-serializable snapshot consumed by the visualizer. */
    private Object state(int cost, String costKind) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("kind", "linkedlist");
        s.put("name", name);
        s.put("size", size);

        List<Object> nodes = new ArrayList<>(size);
        int i = 0;
        for (Node<E> n = head; n != null; n = n.next, i++) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("index", i);
            node.put("value", show(n.value));
            nodes.add(node);
        }
        s.put("nodes", nodes);

        Map<String, Object> lastOp = new LinkedHashMap<>();
        lastOp.put("cost", cost);
        lastOp.put("costKind", costKind);
        s.put("lastOp", lastOp);
        return s;
    }

    private static final class Node<E> {
        final E value;
        Node<E> prev;
        Node<E> next;

        Node(E value) {
            this.value = value;
        }
    }
}
