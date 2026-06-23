import visual.VisualConcurrentCollections;

public class Playground {
    public static void main(String[] args) {
        VisualConcurrentCollections list =
                VisualConcurrentCollections.synchronizedList("syncList", "A", "B");

        list.createFailFastIterator("it", "T1");
        list.addWithSynchronizedLock("T2", "C");

        // The teaching model emits the fail-fast event instead of throwing.
        list.next("it");
    }
}
