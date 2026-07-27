import visual.VisualLatencyHunt;

public class Playground {
    public static void main(String[] args) {
        VisualLatencyHunt hunt = VisualLatencyHunt.reported(
                "shop.example.com", "the product page", "the site is slow");

        hunt.measure("waiting for the first byte (TTFB)", 2400, "the browser network panel");
        hunt.measure("everything the browser does after that", 700, "the browser network panel");
        hunt.split();

        hunt.drillInto("waiting for the first byte (TTFB)", "the request trace");
        hunt.measure("queueing before the handler", 15, "the access log");
        hunt.measure("the product query", 1980, "the trace");
        hunt.measure("the pricing service call", 310, "the trace");
        hunt.measure("template rendering", 95, "the trace");
        hunt.split();

        // Price every idea against the WHOLE request before anybody writes it.
        hunt.ceiling("rewrite the template engine", "template rendering", 4);
        hunt.ceiling("a faster JSON library in the pricing client", "the pricing service call", 2);
        hunt.ceiling("an index for the product query", "the product query", 20);

        // Build the one that felt best anyway, and watch the arithmetic win.
        hunt.fix("rewrite the template engine", 71);
        hunt.remeasure(3030);

        hunt.review();
        System.out.println("A 4x faster 95ms is still 95ms of a 3100ms page.");
    }
}
