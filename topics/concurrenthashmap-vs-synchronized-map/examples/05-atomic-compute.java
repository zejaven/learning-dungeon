import visual.VisualConcurrentMap;

public class Playground {
    public static void main(String[] args) {
        // A synchronized map makes single calls atomic, but a check-then-put
        // pair is not. ConcurrentHashMap offers atomic compound operations like
        // computeIfAbsent / putIfAbsent that lock the bin once around the whole step.
        VisualConcurrentMap sessions = VisualConcurrentMap.concurrentHashMap("sessions");

        // T1 atomically inserts the missing key.
        sessions.computeIfAbsent("T1", "alice", "online");

        // T2 races for the same key, sees it already present, keeps the old value:
        // no lost update, no manual lock around check-then-put.
        sessions.computeIfAbsent("T2", "alice", "elsewhere");

        System.out.println("size = " + sessions.size());
    }
}
