import visual.VisualConcurrentCollections;

public class Playground {
    public static void main(String[] args) {
        VisualConcurrentCollections sessions =
                VisualConcurrentCollections.concurrentMap("sessions");

        sessions.putConcurrent("main", "alice", "online");
        sessions.putConcurrent("main", "bob", "idle");

        sessions.createWeakIterator("scan", "T1");
        System.out.println(sessions.next("scan"));

        sessions.putConcurrent("T2", "carol", "online");
        System.out.println(sessions.next("scan"));
    }
}
