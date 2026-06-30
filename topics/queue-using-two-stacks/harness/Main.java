import java.util.NoSuchElementException;
import visual.TestKit;

/**
 * Hidden test harness for the Two Stack Queue challenge. It calls the learner's
 * Solution.TwoStackQueue and reports each case via visual.TestKit (TEST events).
 * The learner never sees or edits this file.
 */
public class Main {

    public static void main(String[] args) {
        testInitialState();
        testBasicFifo();
        testPeekDoesNotRemove();
        testInterleavedOperations();
        testMultipleTransferCycles();
        testEmptyExceptions();
    }

    private static void testInitialState() {
        Solution.TwoStackQueue q = new Solution.TwoStackQueue();
        TestKit.expect("new queue is empty", true, q.isEmpty());
        TestKit.expect("new queue size", 0, q.size());
    }

    private static void testBasicFifo() {
        Solution.TwoStackQueue q = new Solution.TwoStackQueue();
        q.enqueue(10);
        q.enqueue(20);
        q.enqueue(30);

        TestKit.expect("size after enqueue", 3, q.size());
        TestKit.expect("first dequeue", 10, q.dequeue());
        TestKit.expect("second dequeue", 20, q.dequeue());
        TestKit.expect("third dequeue", 30, q.dequeue());
        TestKit.expect("empty after draining", true, q.isEmpty());
    }

    private static void testPeekDoesNotRemove() {
        Solution.TwoStackQueue q = new Solution.TwoStackQueue();
        q.enqueue(7);
        q.enqueue(8);

        TestKit.expect("first peek", 7, q.peek());
        TestKit.expect("second peek unchanged", 7, q.peek());
        TestKit.expect("size unchanged by peek", 2, q.size());
        TestKit.expect("dequeue after peek", 7, q.dequeue());
    }

    private static void testInterleavedOperations() {
        Solution.TwoStackQueue q = new Solution.TwoStackQueue();
        q.enqueue(1);
        q.enqueue(2);
        q.enqueue(3);

        int first = q.dequeue();
        q.enqueue(4);
        q.enqueue(5);

        TestKit.expect("first before later enqueue", 1, first);
        TestKit.expect("interleaved order", new int[] {2, 3, 4, 5}, dequeueMany(q, 4));
    }

    private static void testMultipleTransferCycles() {
        Solution.TwoStackQueue q = new Solution.TwoStackQueue();
        q.enqueue(-1);
        q.enqueue(0);
        TestKit.expect("negative first", -1, q.dequeue());
        TestKit.expect("zero second", 0, q.dequeue());

        q.enqueue(42);
        TestKit.expect("reused after empty", 42, q.dequeue());
        TestKit.expect("empty after reuse", true, q.isEmpty());
    }

    private static void testEmptyExceptions() {
        Solution.TwoStackQueue q = new Solution.TwoStackQueue();

        TestKit.expect("dequeue empty throws", "NoSuchElementException", dequeueResult(q));
        TestKit.expect("peek empty throws", "NoSuchElementException", peekResult(q));

        q.enqueue(9);
        q.dequeue();
        TestKit.expect("dequeue after drain throws", "NoSuchElementException", dequeueResult(q));
    }

    private static int[] dequeueMany(Solution.TwoStackQueue q, int count) {
        int[] result = new int[count];
        for (int i = 0; i < count; i++) {
            result[i] = q.dequeue();
        }
        return result;
    }

    private static Object dequeueResult(Solution.TwoStackQueue q) {
        try {
            return q.dequeue();
        } catch (NoSuchElementException ex) {
            return "NoSuchElementException";
        } catch (RuntimeException ex) {
            return ex.getClass().getSimpleName();
        }
    }

    private static Object peekResult(Solution.TwoStackQueue q) {
        try {
            return q.peek();
        } catch (NoSuchElementException ex) {
            return "NoSuchElementException";
        } catch (RuntimeException ex) {
            return ex.getClass().getSimpleName();
        }
    }
}
