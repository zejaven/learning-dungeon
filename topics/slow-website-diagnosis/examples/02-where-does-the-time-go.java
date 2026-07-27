import visual.VisualLatencyHunt;

public class Playground {
    public static void main(String[] args) {
        VisualLatencyHunt hunt = VisualLatencyHunt.reported(
                "shop.example.com", "the product page", "the site is slow");
        hunt.target("the product page at p95", 800);

        // The browser's network panel is a complete decomposition of the wait,
        // it needs no setup, and it measures the thing the user complained about.
        hunt.measure("DNS + TCP + TLS", 90, "the browser network panel");
        hunt.measure("waiting for the first byte (TTFB)", 2400, "the browser network panel");
        hunt.measure("downloading the HTML", 60, "the browser network panel");
        hunt.measure("CSS + JS + images", 310, "the browser network panel");
        hunt.measure("rendering + script execution", 240, "the browser network panel");

        hunt.split();

        // A segment leaves with a number attached, never with a reputation.
        hunt.ruleOut("DNS + TCP + TLS", "90ms once, then the connection is reused");
        hunt.ruleOut("CSS + JS + images", "cached after the first visit; the complaint is about repeat visits");

        hunt.split();

        hunt.review();
        System.out.println("The parts add up to 3100ms - and 2400ms of them are on the server side.");
    }
}
