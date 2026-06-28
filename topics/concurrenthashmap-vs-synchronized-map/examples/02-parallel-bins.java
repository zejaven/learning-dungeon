import visual.VisualConcurrentMap;

public class Playground {
    public static void main(String[] args) {
        // ConcurrentHashMap locks ONE bin per write (lock striping), not the
        // whole table. Keys in different bins never wait for each other.
        VisualConcurrentMap sessions = VisualConcurrentMap.concurrentHashMap("sessions");

        // "alice" -> bin 1, "bob" -> bin 4 : different bins.
        sessions.lockBin("T1", "alice");
        sessions.lockBin("T2", "bob"); // proceeds in parallel, no blocking

        sessions.putInBin("T1", "alice", "online");
        sessions.putInBin("T2", "bob", "idle");

        sessions.unlockBin("T1", "alice");
        sessions.unlockBin("T2", "bob");

        System.out.println("size = " + sessions.size());
    }
}
