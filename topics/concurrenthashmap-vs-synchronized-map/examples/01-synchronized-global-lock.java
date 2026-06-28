import visual.VisualConcurrentMap;

public class Playground {
    public static void main(String[] args) {
        // Collections.synchronizedMap wraps EVERY method in synchronized(mutex):
        // there is exactly one table-wide lock, and reads take it too.
        VisualConcurrentMap sessions = VisualConcurrentMap.synchronizedMap("sessions");

        // T1 grabs the single monitor to write one session.
        sessions.lock("T1", "put(alice)");
        sessions.putLocked("T1", "alice", "online");

        // T2 only wants to READ a different key, but the one lock is busy,
        // so it blocks even though it touches nothing T1 touches.
        sessions.lock("T2", "get(bob)"); // SYNC_BLOCKED

        // Only after T1 releases can T2 take the lock and run its read.
        sessions.unlock("T1");
        sessions.lock("T2", "get(bob)");
        sessions.getLocked("T2", "bob");
        sessions.unlock("T2");
    }
}
