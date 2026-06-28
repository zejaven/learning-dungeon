package visual;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A deliberately naive teaching model of a resizable array list.
 *
 * <p>This is not how the JDK {@link java.util.ArrayList} grows. It models the
 * primitive design interviewers often ask about: every append allocates a new
 * array of exactly {@code size + 1}, copies all existing references, then writes
 * the new value. Adding N elements therefore copies
 * {@code 0 + 1 + ... + (N - 1)} references.
 */
public class VisualNaiveArrayList<E> {

    private final String name;
    private Object[] elements;
    private int size;
    private long totalCopies;
    private long totalWrites;

    public VisualNaiveArrayList() {
        this("list");
    }

    public VisualNaiveArrayList(String name) {
        this.name = name;
        this.elements = new Object[0];
        Trace.event("NAIVE_ARRAYLIST_CREATED",
                "Created naive list '" + name + "' with an empty backing array",
                "Создан наивный список '" + name + "' с пустым внутренним массивом",
                List.of(), state(0, 0, "created"));
    }

    /**
     * Appends by allocating a one-slot-larger array and copying all old
     * references first. A batch of N appends is O(N^2).
     */
    public void add(E value) {
        growByOne();
        elements[size] = value;
        size++;
        totalWrites++;
        Trace.event("NAIVE_ARRAYLIST_ADD",
                "Wrote " + show(value) + " into the new last slot; the append itself writes one reference",
                "Записали " + show(value) + " в новую последнюю ячейку; само добавление записывает одну ссылку",
                List.of("slot:" + (size - 1)), state(0, 1, "write"));
    }

    /**
     * Emits the accumulated work after a batch of appends.
     */
    public void reportTotalWork() {
        Trace.event("NAIVE_ARRAYLIST_TOTAL",
                "After " + size + " append(s), total copies are " + totalCopies
                        + " = N * (N - 1) / 2, so adding N elements is O(N^2)",
                "После " + size + " добавлений всего копирований: " + totalCopies
                        + " = N * (N - 1) / 2, поэтому добавление N элементов стоит O(N^2)",
                List.of(), state(0, 0, "total"));
    }

    public int size() {
        return size;
    }

    public long totalCopies() {
        return totalCopies;
    }

    private void growByOne() {
        int copied = size;
        Object[] next = new Object[size + 1];
        for (int i = 0; i < size; i++) {
            next[i] = elements[i];
        }
        elements = next;
        totalCopies += copied;

        Trace.event("NAIVE_ARRAYLIST_GROW",
                "Allocated capacity " + elements.length + " and copied all "
                        + copied + " existing reference(s)",
                "Выделили ёмкость " + elements.length + " и скопировали старые ссылки: "
                        + copied,
                highlightCopiedAndNew(copied), state(copied, 0, "grow"));
    }

    private List<String> highlightCopiedAndNew(int copied) {
        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < copied; i++) {
            tokens.add("slot:" + i);
        }
        tokens.add("slot:" + copied);
        return tokens;
    }

    private Object state(int copied, int write, String phase) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("kind", "naive-arraylist");
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
        lastOp.put("phase", phase);
        lastOp.put("copied", copied);
        lastOp.put("write", write);
        lastOp.put("totalCopies", totalCopies);
        lastOp.put("totalWrites", totalWrites);
        lastOp.put("totalTouches", totalCopies + totalWrites);
        lastOp.put("formula", "0 + 1 + ... + (N - 1) = N * (N - 1) / 2");
        lastOp.put("complexity", "O(N^2)");
        s.put("lastOp", lastOp);
        return s;
    }

    private static String show(Object o) {
        return o == null ? "null" : o.toString();
    }
}
