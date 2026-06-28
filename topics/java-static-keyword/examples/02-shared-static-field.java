import visual.VisualStatic;

public class Playground {
    public static void main(String[] args) {
        VisualStatic tickets = new VisualStatic("Ticket");

        // One shared class-level counter.
        tickets.staticField("nextNumber", "1");

        // Two separate objects receive their own instance field values.
        tickets.newInstance("ticketA", "number=1");
        tickets.staticField("nextNumber", "2");

        tickets.newInstance("ticketB", "number=2");
        tickets.staticField("nextNumber", "3");
    }
}
