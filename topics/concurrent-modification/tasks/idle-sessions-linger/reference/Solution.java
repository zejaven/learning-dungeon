import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** Active sessions in login order, with the minute each one was last seen. */
final class SessionRegistry {
    private final List<String> active = new ArrayList<>();
    private final Map<String, Integer> lastSeen = new HashMap<>();

    void touch(String id, int minute) {
        if (!lastSeen.containsKey(id)) {
            active.add(id);
        }
        lastSeen.put(id, minute);
    }

    List<String> active() {
        return List.copyOf(active);
    }

    boolean isTracked(String id) {
        return lastSeen.containsKey(id);
    }

    /** Drops every session not seen since minute {@code cutoff}; returns how many were dropped. */
    int evictIdle(int cutoff) {
        int dropped = 0;
        Iterator<String> it = active.iterator();
        while (it.hasNext()) {
            String id = it.next();
            if (lastSeen.get(id) < cutoff) {
                it.remove();
                lastSeen.remove(id);
                dropped++;
            }
        }
        return dropped;
    }
}
