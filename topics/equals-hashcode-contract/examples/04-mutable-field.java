import visual.VisualEqualityContract;

public class Playground {
    public static void main(String[] args) {
        VisualEqualityContract<MutableOrderKey> lab = new VisualEqualityContract<>("orders");

        MutableOrderKey key = new MutableOrderKey(1);
        lab.add(key);

        // Mutating a field used by equals/hashCode after insertion is unsafe.
        key.route = 2;

        lab.contains(key);
    }

    static final class MutableOrderKey {
        private int route;

        MutableOrderKey(int route) {
            this.route = route;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof MutableOrderKey other && route == other.route;
        }

        @Override
        public int hashCode() {
            return route;
        }

        @Override
        public String toString() {
            return "MutableOrderKey(" + route + ")";
        }
    }
}
