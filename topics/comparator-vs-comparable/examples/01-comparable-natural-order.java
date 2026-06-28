import visual.VisualOrdering;

public class Playground {
    static class Ticket implements Comparable<Ticket> {
        final String code;
        final int priority;

        Ticket(String code, int priority) {
            this.code = code;
            this.priority = priority;
        }

        @Override
        public int compareTo(Ticket other) {
            int byPriority = Integer.compare(this.priority, other.priority);
            if (byPriority != 0) {
                return byPriority;
            }
            return this.code.compareTo(other.code);
        }

        @Override
        public String toString() {
            return code + "(p=" + priority + ")";
        }
    }

    public static void main(String[] args) {
        VisualOrdering<Ticket> ordering = VisualOrdering.natural(
                "support tickets",
                "lower priority number, then code",
                "меньший номер priority, затем code");

        ordering.add(new Ticket("INC-200", 2));
        ordering.add(new Ticket("INC-100", 1));
        ordering.add(new Ticket("INC-150", 1));
        ordering.sort();
    }
}
