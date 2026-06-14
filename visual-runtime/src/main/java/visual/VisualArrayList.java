package visual;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

/**
 * A <em>teaching model</em> of {@link java.util.ArrayList}. It is NOT the JDK
 * implementation, but it reproduces the ideas an interviewer cares about: a
 * resizable backing array, O(1) random access by index, amortised O(1) append,
 * O(n) insert/remove in the middle (elements must be shifted), and the
 * grow-and-copy that happens when the array is full.
 *
 * <p>Two simplifications are intentional and called out in the topic's
 * explanation: the default capacity is 4 (the JDK uses 10) so a grow is visible
 * after just a few adds, and the growth factor is ×1.5 ({@code cap + cap/2}),
 * matching the JDK.
 *
 * <p>Every mutating call emits a {@link Trace} event whose {@code state}
 * carries the whole backing array plus the <em>cost</em> of the operation (how
 * many elements had to be touched), which is the crux of ArrayList vs
 * LinkedList.
 *
 * @param <E> element type
 */
public class VisualArrayList<E> {

    private static final int DEFAULT_CAPACITY = 4;

    private final String name;
    private Object[] elements;
    private int size;

    public VisualArrayList() {
        this("list");
    }

    public VisualArrayList(String name) {
        this.name = name;
        this.elements = new Object[DEFAULT_CAPACITY];
        Trace.event("ARRAYLIST_CREATED",
                "Created ArrayList '" + name + "' backed by an array of capacity "
                        + elements.length,
                "Создан ArrayList '" + name + "' на массиве ёмкостью "
                        + elements.length,
                List.of(), state(0, "none"));
    }

    /** Appends to the end: amortised O(1), grows the array only when full. */
    public void add(E value) {
        if (size == elements.length) {
            grow();
        }
        elements[size] = value;
        size++;
        Trace.event("ARRAYLIST_ADD",
                "add(" + show(value) + ") wrote into slot " + (size - 1)
                        + " — O(1), no elements shifted",
                "add(" + show(value) + ") записал в ячейку " + (size - 1)
                        + " — O(1), ничего не сдвигалось",
                List.of("slot:" + (size - 1)), state(0, "none"));
    }

    /** Random access by index: O(1) — jump straight to the slot. */
    public E get(int index) {
        checkIndex(index);
        @SuppressWarnings("unchecked")
        E value = (E) elements[index];
        Trace.event("ARRAYLIST_GET",
                "get(" + index + ") jumps straight to slot " + index
                        + " = " + show(value) + " — O(1) random access",
                "get(" + index + ") сразу прыгает в ячейку " + index
                        + " = " + show(value) + " — O(1), произвольный доступ",
                List.of("slot:" + index), state(0, "direct"));
        return value;
    }

    /**
     * Inserts at {@code index}, shifting every following element one slot to
     * the right first: O(n).
     */
    public void add(int index, E value) {
        if (index < 0 || index > size) {
            throw new IndexOutOfBoundsException("index " + index + ", size " + size);
        }
        if (size == elements.length) {
            grow();
        }
        int shifts = size - index;
        for (int i = size; i > index; i--) {
            elements[i] = elements[i - 1];
        }
        elements[index] = value;
        size++;
        Trace.event("ARRAYLIST_INSERT",
                "add(" + index + ", " + show(value) + ") shifted " + shifts
                        + " element(s) right, then wrote slot " + index + " — O(n)",
                "add(" + index + ", " + show(value) + ") сдвинул " + shifts
                        + " элемент(ов) вправо, затем записал ячейку " + index + " — O(n)",
                highlightRange(index, size), state(shifts, "shifts"));
    }

    /**
     * Removes the element at {@code index}, shifting every following element
     * one slot to the left: O(n).
     */
    public E remove(int index) {
        checkIndex(index);
        @SuppressWarnings("unchecked")
        E removed = (E) elements[index];
        int shifts = size - index - 1;
        for (int i = index; i < size - 1; i++) {
            elements[i] = elements[i + 1];
        }
        size--;
        elements[size] = null; // let the old tail slot be collected
        Trace.event("ARRAYLIST_REMOVE",
                "remove(" + index + ") returned " + show(removed) + " and shifted "
                        + shifts + " element(s) left — O(n)",
                "remove(" + index + ") вернул " + show(removed) + " и сдвинул "
                        + shifts + " элемент(ов) влево — O(n)",
                highlightRange(index, size + 1), state(shifts, "shifts"));
        return removed;
    }

    public int size() {
        return size;
    }

    /** Allocates a bigger array (×1.5) and copies every element across. */
    private void grow() {
        int oldCapacity = elements.length;
        int newCapacity = oldCapacity + (oldCapacity >> 1); // ×1.5, like the JDK
        if (newCapacity <= oldCapacity) {
            newCapacity = oldCapacity + 1;
        }
        Object[] bigger = new Object[newCapacity];
        for (int i = 0; i < size; i++) {
            bigger[i] = elements[i];
        }
        elements = bigger;
        Trace.event("ARRAYLIST_GROW",
                "Backing array was full — allocated a new array of capacity "
                        + newCapacity + " and copied all " + size + " element(s)",
                "Массив заполнился — выделен новый массив ёмкостью "
                        + newCapacity + " и скопированы все " + size + " элемент(ов)",
                List.of(), state(size, "copies"));
    }

    private void checkIndex(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("index " + index + ", size " + size);
        }
    }

    private static List<String> highlightRange(int from, int toExclusive) {
        List<String> tokens = new ArrayList<>();
        for (int i = from; i < toExclusive; i++) {
            tokens.add("slot:" + i);
        }
        return tokens;
    }

    private static String show(Object o) {
        return o == null ? "null" : o.toString();
    }

    /** Builds the JSON-serializable snapshot consumed by the visualizer. */
    private Object state(int cost, String costKind) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("kind", "arraylist");
        s.put("name", name);
        s.put("capacity", elements.length);
        s.put("size", size);

        List<Object> slots = new ArrayList<>(elements.length);
        for (int i = 0; i < elements.length; i++) {
            Map<String, Object> slot = new LinkedHashMap<>();
            slot.put("index", i);
            slot.put("value", i < size ? show(elements[i]) : null);
            slots.add(slot);
        }
        s.put("slots", slots);

        Map<String, Object> lastOp = new LinkedHashMap<>();
        lastOp.put("cost", cost);
        lastOp.put("costKind", costKind);
        s.put("lastOp", lastOp);
        return s;
    }
}
