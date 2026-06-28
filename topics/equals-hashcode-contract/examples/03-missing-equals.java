import visual.VisualEqualityContract;

public class Playground {
    public static void main(String[] args) {
        VisualEqualityContract<ReceiptKey> lab = new VisualEqualityContract<>("receipts");

        ReceiptKey first = new ReceiptKey("R-7");
        ReceiptKey sameText = new ReceiptKey("R-7");

        lab.compare(first, sameText);
        lab.add(first);
        lab.add(sameText);
    }

    static final class ReceiptKey {
        private final String number;

        ReceiptKey(String number) {
            this.number = number;
        }

        @Override
        public int hashCode() {
            return number.hashCode();
        }

        @Override
        public String toString() {
            return "ReceiptKey(" + number + ")";
        }
    }
}
