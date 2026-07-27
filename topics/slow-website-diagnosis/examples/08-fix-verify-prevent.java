import visual.VisualLatencyHunt;

public class Playground {
    public static void main(String[] args) {
        VisualLatencyHunt hunt = VisualLatencyHunt.reported(
                "shop.example.com", "the product page", "the site is slow");

        hunt.clarify("which page, and how slow?", "GET /products/{id}, 3.1s at p95");
        hunt.target("the product page at p95", 800);

        hunt.measure("waiting for the first byte (TTFB)", 2400, "the browser network panel");
        hunt.measure("everything the browser does after that", 700, "the browser network panel");
        hunt.split();

        hunt.drillInto("waiting for the first byte (TTFB)", "the request trace");
        hunt.measure("the product query", 1980, "the trace");
        hunt.measure("the pricing service call", 310, "the trace");
        hunt.measure("the rest of the handler", 110, "the trace");
        hunt.split();

        hunt.ceiling("add an index on reviews.product_id", "the product query", 20);
        hunt.confirm("a sequential scan of 2.1M review rows on every product page",
                "EXPLAIN ANALYZE puts 1970ms of the 1980ms query in that one scan");

        hunt.fix("add an index on reviews.product_id", 1881);
        hunt.remeasure(1180);

        hunt.guard("a p95 graph for GET /products/{id}, on the same dashboard as the deploys");
        hunt.guard("an alert when p95 crosses the agreed 800ms budget");
        hunt.guard("one request id logged by the gateway, the app and the database");

        hunt.review();
        System.out.println("61% faster, still over budget - so the next round starts from the new split.");
    }
}
