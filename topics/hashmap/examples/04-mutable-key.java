import visual.VisualHashMap;

public class Playground {
    public static void main(String[] args) {
        VisualHashMap<Key, String> map = new VisualHashMap<>("map");

        Key k = new Key("Alex");
        map.put(k, "admin");

        // Mutating the key changes its hashCode AFTER insertion.
        // Now get() computes a different bucket index and can't find the entry.
        k.name = "Bob";

        System.out.println("get(k) -> " + map.get(k)); // null!
    }

    // A key whose hashCode depends on a mutable field — an anti-pattern.
    static class Key {
        String name;

        Key(String name) {
            this.name = name;
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key other && other.name.equals(name);
        }

        @Override
        public String toString() {
            return "Key(" + name + ")";
        }
    }
}
