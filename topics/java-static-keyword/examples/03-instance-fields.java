import visual.VisualStatic;

public class Playground {
    public static void main(String[] args) {
        VisualStatic sessions = new VisualStatic("Session");

        sessions.newInstance("aliceSession", "user=alice", "status=active");
        sessions.newInstance("bobSession", "user=bob", "status=active");

        // Only Alice's object changes; Bob's object keeps its own status.
        sessions.instanceField("aliceSession", "status", "idle");
    }
}
