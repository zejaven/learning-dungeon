package visual;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A <em>teaching model</em> of a generational JVM heap. It is NOT a real GC: it
 * reproduces the ideas an interviewer cares about — why the heap is split into
 * Eden, two Survivor spaces (S0/S1) and the Old (Tenured) generation, how a
 * minor GC copies live objects and ages them, when an object is promoted, and
 * what a major (full) GC does — while emitting {@link Trace} events so the UI
 * can visualize every step.
 *
 * <p>The generational hypothesis ("most objects die young") is the whole point:
 * fast bump allocation in Eden, cheap minor GCs that touch only the young
 * generation, and rare major GCs over the long-lived Old generation.
 *
 * <p>Sizes are deliberately tiny so collections happen quickly and the picture
 * stays readable. Differences from a real collector are intentional and called
 * out in the topic's explanation (no real reachability graph — you mark garbage
 * with {@link #drop}; no compaction; no OutOfMemoryError).
 */
public class VisualHeap {

    private static final int EDEN_CAPACITY = 4;
    private static final int SURVIVOR_CAPACITY = 3;
    private static final int DEFAULT_TENURING_THRESHOLD = 3;

    private final String name;
    private final int tenuringThreshold;

    private final List<HeapObject> eden = new ArrayList<>();
    private final List<List<HeapObject>> survivors = List.of(new ArrayList<>(), new ArrayList<>());
    private final List<HeapObject> old = new ArrayList<>();

    /** Index of the empty survivor we copy live objects into during the next GC. */
    private int toSurvivor = 0;

    private int nextId = 1;
    private int minorGcCount;
    private int majorGcCount;

    public VisualHeap() {
        this("heap");
    }

    public VisualHeap(String name) {
        this(name, DEFAULT_TENURING_THRESHOLD);
    }

    public VisualHeap(String name, int tenuringThreshold) {
        this.name = name;
        this.tenuringThreshold = tenuringThreshold;
        Trace.event("HEAP_CREATED",
                "Created heap '" + name + "': Eden(" + EDEN_CAPACITY + "), two Survivors("
                        + SURVIVOR_CAPACITY + "), Old. Tenuring threshold " + tenuringThreshold,
                "Создана куча '" + name + "': Eden(" + EDEN_CAPACITY + "), два Survivor("
                        + SURVIVOR_CAPACITY + "), Old. Порог продвижения " + tenuringThreshold,
                List.of("region:eden"), state());
    }

    /** A handle the example code keeps so it can later {@link #drop} the object. */
    public static final class Ref {
        private final HeapObject obj;

        private Ref(HeapObject obj) {
            this.obj = obj;
        }
    }

    /**
     * Allocates a new object in Eden. If Eden is full this first runs a minor GC
     * (just like the real JVM does on an allocation failure).
     */
    public Ref allocate(String label) {
        if (eden.size() >= EDEN_CAPACITY) {
            Trace.event("EDEN_FULL",
                    "Eden is full (" + EDEN_CAPACITY + ") — allocating '" + label
                            + "' triggers a minor GC first",
                    "Eden заполнен (" + EDEN_CAPACITY + ") — выделение '" + label
                            + "' сначала запускает minor GC",
                    List.of("region:eden"), state());
            minorGc();
        }
        HeapObject obj = new HeapObject(nextId++, label);
        eden.add(obj);
        Trace.event("OBJECT_ALLOCATED",
                "Allocated '" + label + "' in Eden (bump-pointer, age 0)",
                "Объект '" + label + "' выделен в Eden (bump-pointer, возраст 0)",
                List.of("region:eden", "obj:o" + obj.id), state());
        return new Ref(obj);
    }

    /** Marks the object as unreachable — i.e. it becomes garbage for the next GC. */
    public void drop(Ref ref) {
        HeapObject obj = ref.obj;
        obj.live = false;
        Trace.event("OBJECT_UNREACHABLE",
                "Reference to '" + obj.label + "' dropped — it is now garbage",
                "Ссылка на '" + obj.label + "' сброшена — теперь это мусор",
                List.of("obj:o" + obj.id), state());
    }

    /** Runs a minor (young-generation) garbage collection on demand. */
    public void gc() {
        minorGc();
    }

    /** Runs a major (full) garbage collection: young generation plus the Old generation. */
    public void fullGc() {
        List<HeapObject> promoted = collectYoung();

        int deadOld = removeDead(old);

        majorGcCount++;
        Trace.event("MAJOR_GC",
                "Major (full) GC #" + majorGcCount + ": collected " + deadOld
                        + " dead Old-gen object(s) and ran a young collection",
                "Major (full) GC #" + majorGcCount + ": собрано " + deadOld
                        + " мёртвых объектов Old и выполнена сборка молодого поколения",
                List.of("region:old"), state());
        announcePromotions(promoted);
    }

    private void minorGc() {
        int dest = toSurvivor; // destination survivor, before collectYoung swaps it
        List<HeapObject> promoted = collectYoung();

        minorGcCount++;
        Trace.event("MINOR_GC",
                "Minor GC #" + minorGcCount + ": Eden + the in-use Survivor were scanned, "
                        + "garbage freed, survivors copied to S" + dest
                        + " and aged",
                "Minor GC #" + minorGcCount + ": просканированы Eden и активный Survivor, "
                        + "мусор освобождён, выжившие скопированы в S" + dest
                        + " и состарены",
                List.of("region:s" + dest), state());
        announcePromotions(promoted);
    }

    /**
     * Copying collection of the young generation. Live objects in Eden and the
     * in-use Survivor are aged and copied to the empty Survivor; objects that
     * reach the tenuring threshold (or that overflow the Survivor space) are
     * promoted to Old. Returns the list of objects promoted in this cycle.
     */
    private List<HeapObject> collectYoung() {
        int to = toSurvivor;
        int from = 1 - to;

        List<HeapObject> live = new ArrayList<>();
        for (HeapObject o : eden) {
            if (o.live) live.add(o);
        }
        for (HeapObject o : survivors.get(from)) {
            if (o.live) live.add(o);
        }

        eden.clear();
        survivors.get(from).clear();
        List<HeapObject> toSpace = survivors.get(to);

        List<HeapObject> promoted = new ArrayList<>();
        for (HeapObject o : live) {
            o.age++;
            if (o.age >= tenuringThreshold) {
                old.add(o);
                promoted.add(o);
            } else if (toSpace.size() < SURVIVOR_CAPACITY) {
                toSpace.add(o);
            } else {
                // Survivor space overflow -> premature promotion to Old.
                o.prematurelyPromoted = true;
                old.add(o);
                promoted.add(o);
            }
        }

        // The "from" survivor is now empty; copy into it next time.
        toSurvivor = from;
        return promoted;
    }

    private void announcePromotions(List<HeapObject> promoted) {
        if (promoted.isEmpty()) {
            return;
        }
        boolean anyOverflow = promoted.stream().anyMatch(o -> o.prematurelyPromoted);
        StringBuilder names = new StringBuilder();
        List<String> highlight = new ArrayList<>();
        highlight.add("region:old");
        for (HeapObject o : promoted) {
            if (names.length() > 0) names.append(", ");
            names.append('\'').append(o.label).append('\'');
            highlight.add("obj:o" + o.id);
        }
        Trace.event("PROMOTION",
                "Promoted to Old: " + names + (anyOverflow
                        ? " (Survivor overflow -> premature promotion)"
                        : " (reached tenuring threshold " + tenuringThreshold + ")"),
                "Продвинуто в Old: " + names + (anyOverflow
                        ? " (переполнение Survivor -> преждевременное продвижение)"
                        : " (достигнут порог продвижения " + tenuringThreshold + ")"),
                highlight, state());
    }

    private static int removeDead(List<HeapObject> region) {
        int before = region.size();
        region.removeIf(o -> !o.live);
        return before - region.size();
    }

    /** Builds the JSON-serializable snapshot consumed by the visualizer. */
    private Object state() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("name", name);
        s.put("tenuringThreshold", tenuringThreshold);
        s.put("minorGcCount", minorGcCount);
        s.put("majorGcCount", majorGcCount);

        List<Object> regions = new ArrayList<>();
        regions.add(region("eden", "Eden", "eden", EDEN_CAPACITY, false, eden));
        regions.add(region("s0", "S0", "survivor", SURVIVOR_CAPACITY, toSurvivor == 1, survivors.get(0)));
        regions.add(region("s1", "S1", "survivor", SURVIVOR_CAPACITY, toSurvivor == 0, survivors.get(1)));
        regions.add(region("old", "Old", "old", -1, false, old));
        s.put("regions", regions);
        return s;
    }

    private static Object region(String id, String label, String kind, int capacity,
                                 boolean active, List<HeapObject> objects) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", id);
        r.put("label", label);
        r.put("kind", kind);
        r.put("capacity", capacity);
        // For a survivor, `active` means it currently holds live objects (the "from" space).
        r.put("active", active);
        List<Object> objs = new ArrayList<>();
        for (HeapObject o : objects) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", "o" + o.id);
            m.put("label", o.label);
            m.put("age", o.age);
            m.put("dead", !o.live);
            objs.add(m);
        }
        r.put("objects", objs);
        return r;
    }

    private static final class HeapObject {
        final int id;
        final String label;
        int age;
        boolean live = true;
        boolean prematurelyPromoted;

        HeapObject(int id, String label) {
            this.id = id;
            this.label = label;
        }
    }
}
