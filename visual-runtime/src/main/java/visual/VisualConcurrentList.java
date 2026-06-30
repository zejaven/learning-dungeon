package visual;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A deterministic teaching model for {@code ConcurrentModificationException}
 * and safe ways to modify a shared list.
 *
 * <p>It models two backing strategies selected by mode:
 * <ul>
 *   <li>{@code FAIL_FAST} mirrors {@link java.util.ArrayList}: a modCount counter
 *       is bumped on every structural change, and each iterator remembers the
 *       modCount it started with (expectedModCount). A {@code next()} whose
 *       expectedModCount no longer matches throws {@code ConcurrentModificationException}.</li>
 *   <li>{@code COPY_ON_WRITE} mirrors {@link java.util.concurrent.CopyOnWriteArrayList}:
 *       a write copies the whole backing array, and an iterator reads a frozen
 *       snapshot taken at creation time, so it never throws.</li>
 * </ul>
 *
 * <p>It does not start real threads; examples pass an actor name so the
 * visualization can show writes and iteration from different threads without
 * depending on scheduler timing.
 */
public class VisualConcurrentList {

    public static final String FAIL_FAST = "FAIL_FAST";
    public static final String COPY_ON_WRITE = "COPY_ON_WRITE";

    private static final int HISTORY_LIMIT = 7;

    private final String name;
    private final String mode;
    private final List<String> elements = new ArrayList<>();
    private final List<Map<String, Object>> history = new ArrayList<>();

    private int modCount;

    // Iterator state (one active iterator at a time is enough for teaching).
    private boolean iterActive;
    private int expectedModCount;
    private int cursor;
    private int lastReturned = -1;
    private List<String> snapshot = new ArrayList<>();

    public VisualConcurrentList(String name) {
        this(name, FAIL_FAST);
    }

    public VisualConcurrentList(String name, String mode) {
        this.name = Objects.requireNonNull(name, "name");
        this.mode = normalizeMode(mode);
        addHistory(name, "CREATE", this.mode);
        Trace.event("LIST_CREATED",
                "Created list '" + name + "' backed by "
                        + (COPY_ON_WRITE.equals(this.mode) ? "CopyOnWriteArrayList" : "a fail-fast ArrayList"),
                "Создан список '" + name + "' на основе "
                        + (COPY_ON_WRITE.equals(this.mode) ? "CopyOnWriteArrayList" : "fail-fast ArrayList"),
                List.of(),
                state());
    }

    public void add(String value) {
        add("main", value);
    }

    public void add(String actor, String value) {
        elements.add(value);
        modCount++;
        if (COPY_ON_WRITE.equals(mode)) {
            addHistory(actor, "COW_ADD", value);
            Trace.event("COW_WRITE_COPY",
                    actor + " adds '" + value + "': CopyOnWriteArrayList copies the whole backing array,"
                            + " so an active iterator keeps its old snapshot",
                    actor + " добавляет '" + value + "': CopyOnWriteArrayList копирует весь backing array,"
                            + " поэтому активный iterator сохраняет свой старый snapshot",
                    List.of("element:" + (elements.size() - 1), "modCount"),
                    state());
            return;
        }
        addHistory(actor, "ADD", value);
        Trace.event("LIST_ADD",
                actor + " adds '" + value + "'; modCount is now " + modCount
                        + (iterActive ? " while an iterator is active" : ""),
                actor + " добавляет '" + value + "'; modCount теперь " + modCount
                        + (iterActive ? ", пока активен iterator" : ""),
                List.of("element:" + (elements.size() - 1), "modCount"),
                state());
    }

    public void remove(String value) {
        remove("main", value);
    }

    public void remove(String actor, String value) {
        int index = elements.indexOf(value);
        if (index < 0) {
            return;
        }
        elements.remove(index);
        modCount++;
        if (COPY_ON_WRITE.equals(mode)) {
            addHistory(actor, "COW_REMOVE", value);
            Trace.event("COW_WRITE_COPY",
                    actor + " removes '" + value + "': the write copies the backing array; iterators are unaffected",
                    actor + " удаляет '" + value + "': запись копирует backing array; на iterators это не влияет",
                    List.of("modCount"),
                    state());
            return;
        }
        addHistory(actor, "REMOVE", value);
        Trace.event("LIST_REMOVE",
                actor + " removes '" + value + "'; modCount is now " + modCount
                        + (iterActive ? " while an iterator is active" : ""),
                actor + " удаляет '" + value + "'; modCount теперь " + modCount
                        + (iterActive ? ", пока активен iterator" : ""),
                List.of("modCount"),
                state());
    }

    /** Starts a fresh iteration (mirrors {@code list.iterator()} / a for-each loop). */
    public void iterator(String actor) {
        iterActive = true;
        cursor = 0;
        lastReturned = -1;
        if (COPY_ON_WRITE.equals(mode)) {
            snapshot = new ArrayList<>(elements);
            addHistory(actor, "ITERATOR", "snapshot");
            Trace.event("ITERATOR_CREATED",
                    actor + " starts iterating; the iterator freezes a snapshot of the current array",
                    actor + " начинает итерацию; iterator замораживает snapshot текущего array",
                    List.of("iterator"),
                    state());
            return;
        }
        expectedModCount = modCount;
        snapshot = new ArrayList<>();
        addHistory(actor, "ITERATOR", "expectedModCount=" + expectedModCount);
        Trace.event("ITERATOR_CREATED",
                actor + " starts iterating; the iterator records expectedModCount = " + expectedModCount,
                actor + " начинает итерацию; iterator запоминает expectedModCount = " + expectedModCount,
                List.of("iterator", "modCount"),
                state());
    }

    public void iterator() {
        iterator("main");
    }

    /** Advances the iterator (mirrors {@code iterator.next()}). */
    public String next(String actor) {
        if (!iterActive) {
            iterator(actor);
        }
        if (COPY_ON_WRITE.equals(mode)) {
            if (cursor >= snapshot.size()) {
                return finishIteration(actor);
            }
            String value = snapshot.get(cursor);
            lastReturned = cursor;
            cursor++;
            boolean diverged = !snapshot.equals(elements);
            if (diverged) {
                addHistory(actor, "COW_SNAPSHOT_READ", value);
                Trace.event("COW_SNAPSHOT_READ",
                        actor + " reads '" + value + "' from its frozen snapshot, even though the live list has changed",
                        actor + " читает '" + value + "' из своего замороженного snapshot, хотя живой список уже изменился",
                        List.of("snapshot:" + lastReturned),
                        state());
            } else {
                addHistory(actor, "NEXT", value);
                Trace.event("ITERATOR_NEXT",
                        actor + " reads '" + value + "' from the snapshot",
                        actor + " читает '" + value + "' из snapshot",
                        List.of("snapshot:" + lastReturned),
                        state());
            }
            return value;
        }

        // Fail-fast: the modCount check happens before returning the next element.
        if (modCount != expectedModCount) {
            addHistory(actor, "CME", "modCount=" + modCount + " expected=" + expectedModCount);
            Trace.event("CONCURRENT_MODIFICATION",
                    actor + ".next() sees modCount " + modCount + " != expectedModCount "
                            + expectedModCount + ", so it throws ConcurrentModificationException",
                    actor + ".next() видит modCount " + modCount + " != expectedModCount "
                            + expectedModCount + ", поэтому бросает ConcurrentModificationException",
                    List.of("iterator", "modCount"),
                    state());
            return null;
        }
        if (cursor >= elements.size()) {
            return finishIteration(actor);
        }
        String value = elements.get(cursor);
        lastReturned = cursor;
        cursor++;
        addHistory(actor, "NEXT", value);
        Trace.event("ITERATOR_NEXT",
                actor + " reads '" + value + "'; modCount still matches expectedModCount",
                actor + " читает '" + value + "'; modCount всё ещё совпадает с expectedModCount",
                List.of("element:" + lastReturned),
                state());
        return value;
    }

    public String next() {
        return next("main");
    }

    /** Removes the last element returned by next() through the iterator itself. */
    public void iteratorRemove(String actor) {
        if (lastReturned < 0) {
            return;
        }
        if (COPY_ON_WRITE.equals(mode)) {
            addHistory(actor, "COW_ITER_REMOVE", "unsupported");
            Trace.event("COW_REMOVE_UNSUPPORTED",
                    actor + " calls iterator.remove() on a CopyOnWriteArrayList iterator, which throws UnsupportedOperationException",
                    actor + " вызывает iterator.remove() у iterator CopyOnWriteArrayList, что бросает UnsupportedOperationException",
                    List.of("iterator"),
                    state());
            return;
        }
        String removed = elements.remove(lastReturned);
        modCount++;
        // The iterator keeps itself in sync: this is why iterator.remove() is safe.
        expectedModCount = modCount;
        cursor = lastReturned;
        lastReturned = -1;
        addHistory(actor, "ITER_REMOVE", removed);
        Trace.event("ITERATOR_REMOVE",
                actor + " removes '" + removed + "' through iterator.remove(); it updates expectedModCount to "
                        + expectedModCount + ", so iteration stays valid",
                actor + " удаляет '" + removed + "' через iterator.remove(); он обновляет expectedModCount до "
                        + expectedModCount + ", поэтому итерация остаётся валидной",
                List.of("iterator", "modCount"),
                state());
    }

    public void iteratorRemove() {
        iteratorRemove("main");
    }

    public int size() {
        return elements.size();
    }

    public int modCount() {
        return modCount;
    }

    private String finishIteration(String actor) {
        iterActive = false;
        addHistory(actor, "DONE", "");
        Trace.event("ITERATION_DONE",
                actor + " finished iterating without an exception",
                actor + " завершил итерацию без исключения",
                List.of(),
                state());
        return null;
    }

    private String normalizeMode(String mode) {
        if (COPY_ON_WRITE.equalsIgnoreCase(mode) || "COW".equalsIgnoreCase(mode)) {
            return COPY_ON_WRITE;
        }
        return FAIL_FAST;
    }

    private void addHistory(String actor, String action, String detail) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("actor", actor);
        item.put("action", action);
        item.put("detail", detail);
        history.add(item);
        if (history.size() > HISTORY_LIMIT) {
            history.remove(0);
        }
    }

    private Object state() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("name", name);
        s.put("mode", mode);
        s.put("modCount", modCount);

        List<Object> elementList = new ArrayList<>();
        for (int i = 0; i < elements.size(); i++) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("index", i);
            item.put("value", elements.get(i));
            elementList.add(item);
        }
        s.put("elements", elementList);

        Map<String, Object> iter = new LinkedHashMap<>();
        iter.put("active", iterActive);
        iter.put("expectedModCount", expectedModCount);
        iter.put("cursor", cursor);
        iter.put("lastReturned", lastReturned);
        iter.put("snapshot", new ArrayList<>(snapshot));
        boolean stale = COPY_ON_WRITE.equals(mode)
                ? (iterActive && !snapshot.equals(elements))
                : (iterActive && modCount != expectedModCount);
        iter.put("stale", stale);
        s.put("iterator", iter);

        s.put("history", new ArrayList<>(history));
        return s;
    }
}
