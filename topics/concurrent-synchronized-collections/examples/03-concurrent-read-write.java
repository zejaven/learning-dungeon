import visual.VisualConcurrentCollections;

public class Playground {
    public static void main(String[] args) {
        VisualConcurrentCollections sessions =
                VisualConcurrentCollections.concurrentMap("sessions");

        sessions.putConcurrent("T1", "alice", "online");
        String value = sessions.getConcurrent("T2", "alice");
        sessions.putConcurrent("T2", "bob", "idle");
        sessions.getConcurrent("T1", "bob");

        System.out.println("alice = " + value);
    }
}
