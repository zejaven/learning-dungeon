import visual.VisualConcurrentCollections;

public class Playground {
    public static void main(String[] args) {
        VisualConcurrentCollections dedupe =
                VisualConcurrentCollections.concurrentMap("dedupe");

        boolean first = dedupe.putIfAbsent("T1", "order-42", "processing");
        boolean second = dedupe.putIfAbsent("T2", "order-42", "duplicate");

        System.out.println("first inserted = " + first);
        System.out.println("second inserted = " + second);
    }
}
