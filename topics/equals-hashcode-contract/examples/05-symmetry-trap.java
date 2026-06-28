import visual.VisualEqualityContract;

public class Playground {
    public static void main(String[] args) {
        VisualEqualityContract<Price> lab = new VisualEqualityContract<>("prices");

        Price regular = new Price(1200);
        Price discounted = new DiscountedPrice(1200, "LUNCH");

        lab.checkSymmetry(regular, discounted);
    }

    static class Price {
        protected final int cents;

        Price(int cents) {
            this.cents = cents;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Price other && cents == other.cents;
        }

        @Override
        public int hashCode() {
            return Integer.hashCode(cents);
        }

        @Override
        public String toString() {
            return "Price(" + cents + ")";
        }
    }

    static final class DiscountedPrice extends Price {
        private final String coupon;

        DiscountedPrice(int cents, String coupon) {
            super(cents);
            this.coupon = coupon;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof DiscountedPrice other
                    && cents == other.cents
                    && coupon.equals(other.coupon);
        }

        @Override
        public int hashCode() {
            return 31 * Integer.hashCode(cents) + coupon.hashCode();
        }

        @Override
        public String toString() {
            return "DiscountedPrice(" + cents + "," + coupon + ")";
        }
    }
}
