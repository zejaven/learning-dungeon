import visual.VisualEquality;

import java.util.Objects;

public class Playground {
    static class Point {
        protected final int x;
        protected final int y;

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

    static class Point3D extends Point {
        private final int z;

        Point3D(int x, int y, int z) {
            super(x, y);
            this.z = z;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Point3D other) {
                return super.equals(other) && z == other.z;
            }
            if (obj instanceof Point other) {
                return super.equals(other);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y, z);
        }
    }

    public static void main(String[] args) {
        VisualEquality viz = new VisualEquality("friendly Point3D transitivity trap");
        Point3D a = new Point3D(1, 2, 10);
        Point b = new Point(1, 2);
        Point3D c = new Point3D(1, 2, 20);

        viz.object("a", a, "a", "x=1", "y=2", "z=10");
        viz.object("b", b, "b", "x=1", "y=2");
        viz.object("c", c, "c", "x=1", "y=2", "z=20");
        viz.compare("a", "b", a.equals(b), "Point3D.equals");
        viz.compare("b", "c", b.equals(c), "Point.equals");
        viz.compare("a", "c", a.equals(c), "Point3D.equals");
        viz.checkTransitivity("a", "b", "c");
    }
}
