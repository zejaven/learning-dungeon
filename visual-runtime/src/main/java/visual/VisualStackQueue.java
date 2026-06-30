package visual;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A teaching model for Stack and Queue behavior. It deliberately focuses on the
 * contract interviewers ask about: stack operations use one end (LIFO), queue
 * operations add at the back and remove at the front (FIFO).
 *
 * <p>The model is not a replacement for {@link java.util.ArrayDeque}; it emits
 * {@link Trace} events so the UI can replay the changing order.
 *
 * @param <E> element type
 */
public final class VisualStackQueue<E> {

    private enum Structure {
        STACK,
        QUEUE
    }

    private final String name;
    private final Structure structure;
    private final Deque<E> items = new ArrayDeque<>();

    private VisualStackQueue(String name, Structure structure) {
        this.name = name;
        this.structure = structure;
        if (structure == Structure.STACK) {
            Trace.event("STACK_CREATED",
                    "Created empty stack '" + name + "'. push(), pop() and peek() work at the top end.",
                    "Создан пустой stack '" + name + "'. push(), pop() и peek() работают с концом top.",
                    List.of(), state("create", null, "CREATE"));
        } else {
            Trace.event("QUEUE_CREATED",
                    "Created empty queue '" + name + "'. offer() adds at the back and poll() removes from the front.",
                    "Создана пустая queue '" + name + "'. offer() добавляет в back, а poll() удаляет из front.",
                    List.of(), state("create", null, "CREATE"));
        }
    }

    public static <E> VisualStackQueue<E> stack(String name) {
        return new VisualStackQueue<>(name, Structure.STACK);
    }

    public static <E> VisualStackQueue<E> queue(String name) {
        return new VisualStackQueue<>(name, Structure.QUEUE);
    }

    public void push(E value) {
        ensureStack("push");
        requireNonNull(value);
        items.addLast(value);
        int index = items.size() - 1;
        Trace.event("STACK_PUSH",
                "push(" + show(value) + ") puts the item on top. Because the stack is LIFO, it is now first for pop().",
                "push(" + show(value) + ") кладет элемент на top. Так как stack работает по LIFO, теперь pop() возьмет его первым.",
                List.of("slot:" + index, "item:" + show(value), "end:top"),
                state("push(" + show(value) + ")", show(value), "LIFO"));
    }

    public E pop() {
        ensureStack("pop");
        if (items.isEmpty()) {
            Trace.event("STACK_POP_EMPTY",
                    "pop() cannot remove anything because the stack is empty.",
                    "pop() не может ничего удалить, потому что stack пуст.",
                    List.of("end:top"), state("pop()", null, "EMPTY"));
            return null;
        }
        E value = items.removeLast();
        Trace.event("STACK_POP",
                "pop() removes " + show(value) + " from the top. This is LIFO: the last pushed item leaves first.",
                "pop() удаляет " + show(value) + " с top. Это LIFO: последним добавили - первым забрали.",
                List.of("end:top"), state("pop()", show(value), "LIFO"));
        return value;
    }

    public void offer(E value) {
        ensureQueue("offer");
        requireNonNull(value);
        items.addLast(value);
        int index = items.size() - 1;
        Trace.event("QUEUE_OFFER",
                "offer(" + show(value) + ") appends the item at the back. Existing front items keep their turn.",
                "offer(" + show(value) + ") добавляет элемент в back. Элементы у front сохраняют свою очередь.",
                List.of("slot:" + index, "item:" + show(value), "end:back"),
                state("offer(" + show(value) + ")", show(value), "FIFO"));
    }

    public E poll() {
        ensureQueue("poll");
        if (items.isEmpty()) {
            Trace.event("QUEUE_POLL_EMPTY",
                    "poll() returns null because the queue is empty.",
                    "poll() возвращает null, потому что queue пуста.",
                    List.of("end:front"), state("poll()", null, "EMPTY"));
            return null;
        }
        E value = items.removeFirst();
        Trace.event("QUEUE_POLL",
                "poll() removes " + show(value) + " from the front. This is FIFO: the oldest offered item leaves first.",
                "poll() удаляет " + show(value) + " из front. Это FIFO: самый ранний добавленный элемент выходит первым.",
                List.of("end:front"), state("poll()", show(value), "FIFO"));
        return value;
    }

    public E peek() {
        if (items.isEmpty()) {
            String event = structure == Structure.STACK ? "STACK_PEEK_EMPTY" : "QUEUE_PEEK_EMPTY";
            String end = structure == Structure.STACK ? "top" : "front";
            Trace.event(event,
                    "peek() returns null because there is no item at the " + end + ".",
                    "peek() возвращает null, потому что на конце " + end + " нет элемента.",
                    List.of("end:" + end), state("peek()", null, "EMPTY"));
            return null;
        }

        if (structure == Structure.STACK) {
            E value = items.peekLast();
            Trace.event("STACK_PEEK",
                    "peek() reads " + show(value) + " at the top without removing it.",
                    "peek() читает " + show(value) + " на top, но не удаляет его.",
                    List.of("slot:" + (items.size() - 1), "item:" + show(value), "end:top"),
                    state("peek()", show(value), "PEEK"));
            return value;
        }

        E value = items.peekFirst();
        Trace.event("QUEUE_PEEK",
                "peek() reads " + show(value) + " at the front without removing it.",
                "peek() читает " + show(value) + " у front, но не удаляет его.",
                List.of("slot:0", "item:" + show(value), "end:front"),
                state("peek()", show(value), "PEEK"));
        return value;
    }

    public int size() {
        return items.size();
    }

    private void ensureStack(String method) {
        if (structure != Structure.STACK) {
            throw new IllegalStateException(method + "() belongs to the stack view");
        }
    }

    private void ensureQueue(String method) {
        if (structure != Structure.QUEUE) {
            throw new IllegalStateException(method + "() belongs to the queue view");
        }
    }

    private void requireNonNull(E value) {
        if (value == null) {
            throw new NullPointerException("VisualStackQueue follows ArrayDeque and does not accept null values");
        }
    }

    private Object state(String method, String result, String rule) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("kind", "stackQueue");
        s.put("name", name);
        s.put("structure", structure == Structure.STACK ? "stack" : "queue");
        s.put("size", items.size());
        s.put("leftEnd", structure == Structure.STACK ? "bottom" : "front");
        s.put("rightEnd", structure == Structure.STACK ? "top" : "back");

        List<Object> itemList = new ArrayList<>(items.size());
        int index = 0;
        for (E item : items) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("index", index);
            node.put("value", show(item));
            node.put("role", roleFor(index, items.size()));
            itemList.add(node);
            index++;
        }
        s.put("items", itemList);

        Map<String, Object> op = new LinkedHashMap<>();
        op.put("method", method);
        op.put("result", result);
        op.put("rule", rule);
        op.put("cost", "O(1)");
        s.put("lastOperation", op);
        return s;
    }

    private String roleFor(int index, int size) {
        if (structure == Structure.STACK) {
            if (index == size - 1) {
                return "top";
            }
            if (index == 0) {
                return "bottom";
            }
            return "middle";
        }
        if (index == 0) {
            return "front";
        }
        if (index == size - 1) {
            return "back";
        }
        return "middle";
    }

    private static String show(Object value) {
        return String.valueOf(value);
    }
}
