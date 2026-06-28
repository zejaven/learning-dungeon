import visual.VisualEqualityContract;

public class Playground {
    public static void main(String[] args) {
        VisualEqualityContract<UserKey> lab = new VisualEqualityContract<>("users");

        UserKey first = new UserKey(42, "eu");
        UserKey sameValue = new UserKey(42, "eu");

        lab.compare(first, sameValue);
        lab.add(first);
        lab.add(sameValue);
        lab.contains(new UserKey(42, "eu"));
    }

    static final class UserKey {
        private final int id;
        private final String region;

        UserKey(int id, String region) {
            this.id = id;
            this.region = region;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof UserKey other
                    && id == other.id
                    && region.equals(other.region);
        }

        @Override
        public int hashCode() {
            int result = Integer.hashCode(id);
            result = 31 * result + region.hashCode();
            return result;
        }

        @Override
        public String toString() {
            return "UserKey(" + id + "," + region + ")";
        }
    }
}
