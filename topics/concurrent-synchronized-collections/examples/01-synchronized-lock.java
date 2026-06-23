import visual.VisualConcurrentCollections;

public class Playground {
    public static void main(String[] args) {
        VisualConcurrentCollections list =
                VisualConcurrentCollections.synchronizedList("syncList", "A");

        list.beginSynchronizedOperation("T1", "add(B)");
        list.beginSynchronizedOperation("T2", "add(C)");

        list.addToSynchronizedList("T1", "B");
        list.endSynchronizedOperation("T1");

        list.beginSynchronizedOperation("T2", "add(C)");
        list.addToSynchronizedList("T2", "C");
        list.endSynchronizedOperation("T2");

        System.out.println("size = " + list.size());
    }
}
