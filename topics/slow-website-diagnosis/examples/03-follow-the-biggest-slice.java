import visual.VisualLatencyHunt;

public class Playground {
    public static void main(String[] args) {
        VisualLatencyHunt hunt = VisualLatencyHunt.reported(
                "shop.example.com", "the product page", "the site is slow");

        // Level 1: the browser splits the wait into "the server" and "the browser".
        hunt.measure("waiting for the first byte (TTFB)", 2400, "the browser network panel");
        hunt.measure("everything the browser does after that", 700, "the browser network panel");
        hunt.split();

        // Level 2: the same question one level down - where inside the server?
        hunt.drillInto("waiting for the first byte (TTFB)", "the request trace");
        hunt.measure("queueing before the handler", 15, "the access log");
        hunt.measure("the product query", 1980, "the trace");
        hunt.measure("the pricing service call", 310, "the trace");
        hunt.measure("template rendering", 95, "the trace");
        hunt.split();

        // Level 3: and inside the query - where does the database spend it?
        hunt.drillInto("the product query", "EXPLAIN ANALYZE");
        hunt.measure("the index scan on products.id", 10, "EXPLAIN ANALYZE");
        hunt.measure("the sequential scan on reviews.product_id", 1970, "EXPLAIN ANALYZE");
        hunt.split();

        hunt.confirm("reviews.product_id has no index, so every product page scans the whole table",
                "EXPLAIN ANALYZE: Seq Scan on reviews, 2.1M rows, 1970ms of the 2400ms first byte");

        hunt.review();
        System.out.println("Three splits turned 'the site is slow' into one missing index.");
    }
}
