import visual.VisualCoroutines;

public class Playground {
    public static void main(String[] args) {
        // One Default worker on purpose, so there is nowhere to hide.
        VisualCoroutines rt = VisualCoroutines.runtime(1, 4);

        // launch { } returns immediately; only the first one gets the thread.
        String feed = rt.launch("Default", "loadFeed");
        String avatar = rt.launch("Default", "loadAvatar");
        String badges = rt.launch("Default", "loadBadges");

        // Each delay(...) hands the single thread to whoever is queued behind it.
        rt.suspendAt(feed, "delay(200)");
        rt.suspendAt(avatar, "httpGet(/avatar)");
        rt.suspendAt(badges, "httpGet(/badges)");

        // Three coroutines alive, zero threads busy: that is the point.
        rt.resume(feed);
        rt.resume(avatar);
        rt.resume(badges);

        rt.complete(feed);
        rt.complete(avatar);
        rt.complete(badges);

        rt.report();
        System.out.println("Three concurrent operations, one thread, and it was idle in the middle.");
    }
}
