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
            if (obj == null || obj.getClass() != getClass()) {
                return false;
            }
            Point other = (Point) obj;
            return x == other.x && y == other.y;
        }

        @Override
        public int hashCode() {
            return Objects.hash(getClass(), x, y);
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
            if (obj == null || obj.getClass() != getClass()) {
                return false;
            }
            Point3D other = (Point3D) obj;
            return x == other.x && y == other.y && z == other.z;
        }

        @Override
        public int hashCode() {
            return Objects.hash(getClass(), x, y, z);
        }
    }

    public static void main(String[] args) {
        VisualEquality viz = new VisualEquality("getClass exact type boundary");
        Point p = new Point(1, 2);
        Point3D p3 = new Point3D(1, 2, 7);

        viz.object("p", p, "p", "x=1", "y=2");
        viz.object("p3", p3, "p3", "x=1", "y=2", "z=7");
        viz.compareWithGetClass("p", "p3", p.equals(p3), "Point.equals");
        viz.compareWithGetClass("p3", "p", p3.equals(p), "Point3D.equals");
        viz.checkSymmetry("p", "p3");
    }
}
