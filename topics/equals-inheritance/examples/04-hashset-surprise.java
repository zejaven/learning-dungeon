import visual.VisualEquality;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

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
            if (!(obj instanceof Point3D other)) {
                return false;
            }
            return super.equals(other) && z == other.z;
        }
    }

    public static void main(String[] args) {
        VisualEquality viz = new VisualEquality("HashSet with asymmetric equals");
        Point p = new Point(1, 2);
        Point3D p3 = new Point3D(1, 2, 7);

        viz.object("p", p, "p", "x=1", "y=2");
        viz.object("p3", p3, "p3", "x=1", "y=2", "z=7");
        viz.compare("p", "p3", p.equals(p3), "Point.equals");
        viz.compare("p3", "p", p3.equals(p), "Point3D.equals");
        viz.checkSymmetry("p", "p3");

        Set<Point> setWithPoint = new HashSet<>();
        setWithPoint.add(p);
        viz.collectionProbe("setWithPoint.contains(p3)", setWithPoint.contains(p3));

        Set<Point> setWith3d = new HashSet<>();
        setWith3d.add(p3);
        viz.collectionProbe("setWith3d.contains(p)", setWith3d.contains(p));
    }
}
