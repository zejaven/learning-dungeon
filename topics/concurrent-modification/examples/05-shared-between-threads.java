import visual.VisualConcurrentList;

public class Playground {
    public static void main(String[] args) {
        // A list shared between threads: a Writer mutates while a Reader iterates.
        // CopyOnWriteArrayList makes this safe without any explicit locking.
        VisualConcurrentList shared = new VisualConcurrentList("shared", VisualConcurrentList.COPY_ON_WRITE);

        shared.add("Writer", "order-1");
        shared.add("Writer", "order-2");

        shared.iterator("Reader"); // Reader freezes its snapshot
        shared.next("Reader");     // Reader reads "order-1"
        shared.add("Writer", "order-3"); // Writer adds concurrently -> array copy
        shared.next("Reader");     // Reader still reads "order-2" from its snapshot
        shared.next("Reader");     // snapshot done, no exception
    }
}
