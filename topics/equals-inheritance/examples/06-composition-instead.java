import visual.VisualEquality;

import java.util.Objects;

public class Playground {
    static final class Point {
        private final int x;
        private final int y;

        Point(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof Point other)) {
                return false;
            }
            return x == other.x && y == other.y;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y);
        }
    }

    static final class Point3D {
        private final Point xy;
        private final int z;

        Point3D(int x, int y, int z) {
            this.xy = new Point(x, y);
            this.z = z;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof Point3D other)) {
                return false;
            }
            return xy.equals(other.xy) && z == other.z;
        }

        @Override
        public int hashCode() {
            return Objects.hash(xy, z);
        }
    }

    public static void main(String[] args) {
        VisualEquality viz = new VisualEquality("composition boundary");
        Point p = new Point(1, 2);
        Point3D p3 = new Point3D(1, 2, 7);

        viz.object("p", p, "p", "x=1", "y=2");
        viz.object("p3", p3, "p3", "xy=(1,2)", "z=7");
        viz.compareComposition("p", "p3", p.equals(p3), "Point.equals");
        viz.compareComposition("p3", "p", p3.equals(p), "Point3D.equals");
        viz.checkSymmetry("p", "p3");
    }
}
