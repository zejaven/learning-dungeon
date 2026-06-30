import java.util.ArrayDeque;
import java.util.Deque;
import java.util.NoSuchElementException;

public class Solution {

    /**
     * Implement a FIFO queue using only stack operations on two Deque instances.
     *
     * Rules:
     *   - enqueue(value) adds value to the back of the queue.
     *   - dequeue() removes and returns the oldest queued value.
     *   - peek() returns the oldest queued value without removing it.
     *   - dequeue() and peek() should throw NoSuchElementException when empty.
     *
     * Use push(), pop(), and peek() on the stacks. The intended trick is to move
     * all values from inStack to outStack only when outStack is empty.
     */
    public static class TwoStackQueue {
        private final Deque<Integer> inStack = new ArrayDeque<>();
        private final Deque<Integer> outStack = new ArrayDeque<>();

        public void enqueue(int value) {
            // TODO: implement me, then press "Run tests".
        }

        public int dequeue() {
            // TODO: implement me, then press "Run tests".
            return 0;
        }

        public int peek() {
            // TODO: implement me, then press "Run tests".
            return 0;
        }

        public boolean isEmpty() {
            // TODO: implement me, then press "Run tests".
            return true;
        }

        public int size() {
            // TODO: implement me, then press "Run tests".
            return 0;
        }

        private void moveToOutStackIfNeeded() {
            // TODO: move from inStack to outStack only when outStack is empty.
        }
    }
}
