import visual.TestKit;

import java.util.HashSet;
import java.util.Set;

/** Hidden tests for the Point / ColorPoint task. */
public class Main {

    public static void main(String[] args) {
        TestKit.run(
                TestKit.test("points with the same coordinates are equal", true,
                        () -> new Point(1, 2).equals(new Point(1, 2))),
                TestKit.test("colored points with the same fields are equal", true,
                        () -> new ColorPoint(1, 2, "red").equals(new ColorPoint(1, 2, "red"))),
                TestKit.test("a different color is a different point", false,
                        () -> new ColorPoint(1, 2, "red").equals(new ColorPoint(1, 2, "blue"))),
                TestKit.test("a point does not equal a colored point", false,
                        () -> new Point(1, 2).equals(new ColorPoint(1, 2, "red"))),
                TestKit.test("a colored point does not equal a point", false,
                        () -> new ColorPoint(1, 2, "red").equals(new Point(1, 2))),
                TestKit.test("a set keeps a point and a colored point apart", 2, () -> {
                    Set<Point> points = new HashSet<>();
                    points.add(new Point(1, 2));
                    points.add(new ColorPoint(1, 2, "red"));
                    return points.size();
                })
        );
    }
}
