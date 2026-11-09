import visual.TestKit;

/** Hidden tests for the SessionRegistry task. */
public class Main {

    /** Sessions a, b, c, ... in login order, last seen at the given minutes. */
    private static SessionRegistry registry(int... minutes) {
        SessionRegistry r = new SessionRegistry();
        for (int i = 0; i < minutes.length; i++) {
            r.touch(String.valueOf((char) ('a' + i)), minutes[i]);
        }
        return r;
    }

    /** "dropped [remaining sessions]" after one eviction. */
    private static String evict(SessionRegistry r, int cutoff) {
        int dropped = r.evictIdle(cutoff);
        return dropped + " " + r.active();
    }

    public static void main(String[] args) {
        TestKit.run(
                TestKit.test("nothing is idle, nothing is dropped", "0 [a, b, c]",
                        () -> evict(registry(20, 30, 40), 10)),
                TestKit.test("the first session is idle", "1 [b, c]",
                        () -> evict(registry(5, 30, 40), 10)),
                TestKit.test("the last two sessions are idle", "2 [a]",
                        () -> evict(registry(30, 5, 6), 10)),
                TestKit.test("two idle sessions next to each other", "2 [c, d]",
                        () -> evict(registry(1, 2, 30, 40), 10)),
                TestKit.test("idle sessions far apart", "2 [b, d]",
                        () -> evict(registry(1, 30, 2, 40), 10)),
                TestKit.test("every session is idle", "3 []",
                        () -> evict(registry(1, 2, 3), 10)),
                TestKit.test("a dropped session forgets its last-seen time", "false true false",
                        () -> {
                            SessionRegistry r = registry(1, 30, 2);
                            r.evictIdle(10);
                            return r.isTracked("a") + " " + r.isTracked("b") + " " + r.isTracked("c");
                        })
        );
    }
}
