package visual;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A <em>teaching model</em> showing how the JVM maps an {@code ArrayList} index
 * to a concrete object. It is NOT the JDK implementation; it makes the one idea
 * an interviewer cares about concrete: the backing {@code Object[]} is a single
 * contiguous block of fixed-width reference slots, so {@code get(i)} is a bounds
 * check followed by a single address computation
 * ({@code base + header + i * scale}) and a dereference — never a scan.
 *
 * <p>The addresses, header size and reference scale below are illustrative but
 * realistic (HotSpot with compressed oops uses a 16-byte array header and a
 * 4-byte reference). The objects "live" at separate heap addresses; each slot
 * stores only the reference to its object, never the object inline. These
 * simplifications are called out in the topic's explanation.
 *
 * <p>Every operation emits a {@link Trace} event whose {@code state} carries the
 * whole backing array plus the arithmetic of the last access, which is the crux
 * of why arrays give O(1) random access.
 */
public class VisualArrayIndexing {

    /** Base address of the backing array object (start of its header). */
    private static final long BASE_ADDRESS = 0x1000L;
    /** Bytes of array object header before element [0] (mark + klass + length). */
    private static final int HEADER_BYTES = 16;
    /** Bytes per reference slot (compressed oops). */
    private static final int REF_SCALE = 4;
    /** Where the referenced objects live on the heap, far from the array. */
    private static final long HEAP_BASE = 0x5000L;
    private static final int HEAP_STRIDE = 0x40;

    private final String name;
    private final String[] values; // displayed object content per slot; null = empty
    private final long[] refs;     // heap pointer stored in each slot; 0 = null
    private final int capacity;
    private int size;
    private long nextHeapAddress = HEAP_BASE;

    public VisualArrayIndexing(String name, int capacity) {
        this.name = name;
        this.capacity = capacity;
        this.values = new String[capacity];
        this.refs = new long[capacity];
        Trace.event("ARRAY_INDEX_CREATED",
                "Allocated backing array '" + name + "' of capacity " + capacity
                        + " at base " + hex(BASE_ADDRESS) + "; every slot is a "
                        + REF_SCALE + "-byte reference",
                "Выделен внутренний массив '" + name + "' ёмкостью " + capacity
                        + " по базовому адресу " + hex(BASE_ADDRESS) + "; каждая ячейка — "
                        + REF_SCALE + "-байтная ссылка",
                List.of(), state(null));
    }

    /** Places an object on the heap and stores its reference in the next slot. */
    public void store(String value) {
        if (size == capacity) {
            throw new IllegalStateException("backing array is full");
        }
        int index = size;
        long heapAddr = nextHeapAddress;
        nextHeapAddress += HEAP_STRIDE;
        values[index] = value;
        refs[index] = heapAddr;
        size++;
        Trace.event("ARRAY_STORE_REF",
                "Object " + value + " lives on the heap at " + hex(heapAddr)
                        + "; slot [" + index + "] stores that reference, not the object",
                "Объект " + value + " лежит в куче по адресу " + hex(heapAddr)
                        + "; ячейка [" + index + "] хранит эту ссылку, а не сам объект",
                List.of("slot:" + index),
                lastOp("store", index, null, slotAddress(index)));
    }

    /** Reads index {@code i}: bounds check, address arithmetic, then dereference. */
    public String get(int index) {
        // 1. Bounds check: is the index inside [0, size)?
        boolean inBounds = index >= 0 && index < size;
        Trace.event("ARRAY_BOUNDS_CHECK",
                "Bounds check: 0 <= " + index + " < size(" + size + ") is " + inBounds,
                "Проверка границ: 0 <= " + index + " < size(" + size + ") — " + inBounds,
                inBounds ? List.of("slot:" + index) : List.of(),
                lastOp("bounds", index, null, inBounds ? slotAddress(index) : null));

        if (!inBounds) {
            Trace.event("ARRAY_OUT_OF_BOUNDS",
                    "Index " + index + " is outside [0, " + size
                            + ") — throws ArrayIndexOutOfBoundsException, no address is computed",
                    "Индекс " + index + " вне [0, " + size
                            + ") — бросается ArrayIndexOutOfBoundsException, адрес не вычисляется",
                    List.of(), lastOp("oob", index, null, null));
            throw new IndexOutOfBoundsException(
                    "Index " + index + " out of bounds for length " + size);
        }

        // 2. Address arithmetic: a single multiply-add, independent of size.
        long address = slotAddress(index);
        String formula = hex(BASE_ADDRESS) + " + " + HEADER_BYTES + " + " + index
                + " * " + REF_SCALE + " = " + hex(address);
        Trace.event("ARRAY_ADDRESS_CALC",
                "Slot address = base + header + index * scale = " + formula
                        + " — one multiply-add, so O(1)",
                "Адрес ячейки = base + header + index * scale = " + formula
                        + " — одно умножение со сложением, поэтому O(1)",
                List.of("slot:" + index), lastOp("address", index, formula, address));

        // 3. Dereference: read the pointer in that slot, follow it to the object.
        String value = values[index];
        Trace.event("ARRAY_READ_REF",
                "Read reference " + hex(refs[index]) + " at " + hex(address)
                        + " and followed it to object " + value,
                "Прочитали ссылку " + hex(refs[index]) + " по адресу " + hex(address)
                        + " и перешли по ней к объекту " + value,
                List.of("slot:" + index), lastOp("read", index, formula, address));
        return value;
    }

    public int size() {
        return size;
    }

    /** The crux: index -> address is pure arithmetic, no scanning. */
    private long slotAddress(int index) {
        return BASE_ADDRESS + HEADER_BYTES + (long) index * REF_SCALE;
    }

    private static String hex(long addr) {
        return "0x" + Long.toHexString(addr).toUpperCase();
    }

    /** Builds the snapshot with the arithmetic of the last operation attached. */
    private Object lastOp(String kind, int index, String formula, Long address) {
        Map<String, Object> op = new LinkedHashMap<>();
        op.put("kind", kind);
        op.put("index", index);
        op.put("formula", formula);
        op.put("address", address == null ? null : hex(address));
        return state(op);
    }

    /** Builds the JSON-serializable snapshot consumed by the visualizer. */
    private Object state(Object lastOp) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("kind", "array-indexing");
        s.put("name", name);
        s.put("base", hex(BASE_ADDRESS));
        s.put("header", HEADER_BYTES);
        s.put("scale", REF_SCALE);
        s.put("size", size);
        s.put("capacity", capacity);

        List<Object> slots = new ArrayList<>(capacity);
        for (int i = 0; i < capacity; i++) {
            Map<String, Object> slot = new LinkedHashMap<>();
            slot.put("index", i);
            slot.put("address", hex(slotAddress(i)));
            slot.put("ref", i < size ? hex(refs[i]) : null);
            slot.put("value", i < size ? values[i] : null);
            slots.add(slot);
        }
        s.put("slots", slots);
        s.put("lastOp", lastOp);
        return s;
    }
}
