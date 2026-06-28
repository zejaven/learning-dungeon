import visual.VisualEqualityContract;

public class Playground {
    public static void main(String[] args) {
        VisualEqualityContract<BadTicketKey> lab = new VisualEqualityContract<>("tickets");

        BadTicketKey first = new BadTicketKey("T-100", 1);
        BadTicketKey sameTicketDifferentHash = new BadTicketKey("T-100", 2);

        lab.compare(first, sameTicketDifferentHash);
        lab.add(first);
        lab.add(sameTicketDifferentHash);
    }

    static final class BadTicketKey {
        private final String number;
        private final int forcedHash;

        BadTicketKey(String number, int forcedHash) {
            this.number = number;
            this.forcedHash = forcedHash;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof BadTicketKey other && number.equals(other.number);
        }

        @Override
        public int hashCode() {
            return forcedHash;
        }

        @Override
        public String toString() {
            return "BadTicketKey(" + number + "," + forcedHash + ")";
        }
    }
}
