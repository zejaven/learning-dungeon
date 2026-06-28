import visual.VisualConcurrentMap;

public class Playground {
    public static void main(String[] args) {
        // Striping is not magic: two keys that hash to the SAME bin still
        // serialize. Only the writers of that one bin wait, though.
        VisualConcurrentMap sessions = VisualConcurrentMap.concurrentHashMap("sessions");

        // "alice" -> bin 1 and "carol" -> bin 1 : same bin.
        sessions.lockBin("T1", "alice");
        sessions.lockBin("T2", "carol"); // CHM_BIN_BLOCKED on bin 1

        sessions.putInBin("T1", "alice", "online");
        sessions.unlockBin("T1", "alice");

        // After T1 frees bin 1, T2 can finally take it.
        sessions.lockBin("T2", "carol");
        sessions.putInBin("T2", "carol", "away");
        sessions.unlockBin("T2", "carol");
    }
}
