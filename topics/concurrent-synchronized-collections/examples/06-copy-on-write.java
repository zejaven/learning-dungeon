import visual.VisualConcurrentCollections;

public class Playground {
    public static void main(String[] args) {
        VisualConcurrentCollections listeners =
                VisualConcurrentCollections.copyOnWriteList("listeners", "audit", "email");

        listeners.createSnapshotIterator("notify", "T1");
        listeners.addCopyOnWrite("T2", "metrics");

        System.out.println(listeners.next("notify"));
        System.out.println(listeners.next("notify"));
    }
}
