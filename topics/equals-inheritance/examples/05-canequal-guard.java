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
            if (!other.canEqual(this)) {
                return false;
            }
            return x == other.x && y == other.y;
        }

        protected boolean canEqual(Object other) {
            return other instanceof Point;
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
            if (!(obj instanceof Point3D other)) {
                return false;
            }
            return other.canEqual(this) && super.equals(other) && z == other.z;
        }

        @Override
        protected boolean canEqual(Object other) {
            return other instanceof Point3D;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y, z);
        }
    }

    public static void main(String[] args) {
        VisualEquality viz = new VisualEquality("canEqual guard");
        Point p = new Point(1, 2);
        Point3D p3 = new Point3D(1, 2, 7);
        Point3D same = new Point3D(1, 2, 7);

        viz.object("p", p, "p", "x=1", "y=2");
        viz.object("p3", p3, "p3", "x=1", "y=2", "z=7");
        viz.object("same", same, "same", "x=1", "y=2", "z=7");
        viz.compareWithCanEqual("p", "p3", p.equals(p3), "Point.equals");
        viz.compareWithCanEqual("p3", "p", p3.equals(p), "Point3D.equals");
        viz.checkSymmetry("p", "p3");
        viz.compareWithCanEqual("p3", "same", p3.equals(same), "Point3D.equals");
    }
}
