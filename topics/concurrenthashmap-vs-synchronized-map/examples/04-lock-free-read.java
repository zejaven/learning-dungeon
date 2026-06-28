import visual.VisualConcurrentMap;

public class Playground {
    public static void main(String[] args) {
        // ConcurrentHashMap.get() takes no lock at all (volatile reads), so a
        // reader never blocks behind a writer -- the opposite of a synchronized map.
        VisualConcurrentMap sessions = VisualConcurrentMap.concurrentHashMap("sessions");

        // T1 is mid-write, holding the lock on bin 1.
        sessions.lockBin("T1", "alice");
        sessions.putInBin("T1", "alice", "online");

        // T2 and T3 still read freely, including the very bin being written.
        sessions.get("T2", "bob");
        sessions.get("T3", "alice"); // lock-free read on the locked bin

        sessions.unlockBin("T1", "alice");
    }
}
