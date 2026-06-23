import visual.VisualHashMap;

public class Playground {
    public static void main(String[] args) {
        VisualHashMap<Key, String> sessions = new VisualHashMap<>("sessions");

        Key key = new Key("user-1");
        sessions.put(key, "active");

        // The key now belongs to a different bucket according to hashCode().
        key.id = "user-2";

        System.out.println("changed key -> " + sessions.get(key));
    }

    static class Key {
        String id;

        Key(String id) {
            this.id = id;
        }

        @Override
        public int hashCode() {
            return id.hashCode();
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Key key && key.id.equals(id);
        }

        @Override
        public String toString() {
            return "Key(" + id + ")";
        }
    }
}
