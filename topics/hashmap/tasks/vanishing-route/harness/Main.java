import visual.TestKit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Hidden tests for the RouteKey task. */
public class Main {

    private static List<String> mutable(String... stops) {
        return new ArrayList<>(List.of(stops));
    }

    public static void main(String[] args) {
        TestKit.run(
                TestKit.test("routes with the same stops are equal", true,
                        () -> new RouteKey(mutable("A", "B")).equals(new RouteKey(List.of("A", "B")))),
                TestKit.test("the order of stops matters", false,
                        () -> new RouteKey(mutable("A", "B")).equals(new RouteKey(mutable("B", "A")))),
                TestKit.test("changing the caller's list does not lose the entry", 7, () -> {
                    Map<RouteKey, Integer> cache = new HashMap<>();
                    List<String> stops = mutable("A", "B");
                    cache.put(new RouteKey(stops), 7);
                    stops.add("C");
                    return cache.get(new RouteKey(List.of("A", "B")));
                }),
                TestKit.test("changing the caller's list does not change the key", List.of("A", "B"), () -> {
                    List<String> stops = mutable("A", "B");
                    RouteKey key = new RouteKey(stops);
                    stops.clear();
                    return key.stops();
                }),
                TestKit.test("the getter cannot change the key", 7, () -> {
                    Map<RouteKey, Integer> cache = new HashMap<>();
                    RouteKey key = new RouteKey(mutable("A", "B"));
                    cache.put(key, 7);
                    try {
                        key.stops().add("C");
                    } catch (UnsupportedOperationException expected) {
                        // refusing is as good as handing out a copy
                    }
                    return cache.get(new RouteKey(List.of("A", "B")));
                })
        );
    }
}
