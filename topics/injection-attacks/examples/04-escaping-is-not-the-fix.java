import visual.VisualInjection;

public class Playground {
    public static void main(String[] args) {
        // Hand-written escaping: double every quote before concatenating.
        VisualInjection app = VisualInjection.app().escapeQuotes();

        // Inside a quoted string literal that is the correct rule, and it
        // holds: the payload stays one literal and matches nobody.
        app.findByNameConcatenated("' OR '1'='1");

        // Now the same escaper on a numeric column. A number in SQL is
        // written without quotes, so there is no quote to close -- and the
        // escaper, which only knows about quotes, has nothing to do. The
        // value is already standing next to the operators.
        app.findByIdConcatenated("3");
        app.findByIdConcatenated("3 OR 1=1");

        app.report();
        System.out.println("Escaping is one rule per context; the value moved contexts.");
    }
}
