import visual.VisualOrdering;

import java.util.Comparator;

public class Playground {
    static class Ticket {
        final String code;
        final int ageHours;

        Ticket(String code, int ageHours) {
            this.code = code;
            this.ageHours = ageHours;
        }

        int ageHours() {
            return ageHours;
        }

        String code() {
            return code;
        }

        @Override
        public String toString() {
            return code + "(" + ageHours + "h)";
        }
    }

    public static void main(String[] args) {
        Comparator<Ticket> oldestFirst = Comparator
                .comparingInt(Ticket::ageHours)
                .reversed()
                .thenComparing(Ticket::code);

        VisualOrdering<Ticket> ordering = VisualOrdering.usingComparator(
                "support queue",
                oldestFirst,
                "oldest ticket first, then code",
                "самая старая заявка первой, затем code");

        ordering.add(new Ticket("INC-10", 4));
        ordering.add(new Ticket("INC-20", 9));
        ordering.add(new Ticket("INC-15", 9));
        ordering.sort();
    }
}
