import java.util.Objects;
import visual.VisualHashMap;

public class Playground {
    public static void main(String[] args) {
        VisualHashMap<CustomerKey, String> cache = new VisualHashMap<>("cache");

        CustomerKey key = new CustomerKey("alice@example.com");
        cache.put(key, "profile");
        System.out.println("before change -> " + cache.get(key));

        key.email = "alice@new-domain.test";
        System.out.println("after change -> " + cache.get(key));
    }

    static final class CustomerKey {
        String email;

        CustomerKey(String email) {
            this.email = email;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof CustomerKey that)) {
                return false;
            }
            return Objects.equals(email, that.email);
        }

        @Override
        public int hashCode() {
            return Objects.hash(email);
        }

        @Override
        public String toString() {
            return email;
        }
    }
}
