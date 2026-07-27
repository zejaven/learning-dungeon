import visual.VisualLatencyHunt;

public class Playground {
    public static void main(String[] args) {
        VisualLatencyHunt hunt = VisualLatencyHunt.reported(
                "shop.example.com", "the category page", "the page takes ages to appear");
        hunt.target("first contentful paint at p95", 1500);

        hunt.measure("DNS + TCP + TLS", 80, "the browser network panel");
        hunt.measure("waiting for the first byte (TTFB)", 180, "the browser network panel");
        hunt.measure("the render-blocking JS bundle", 1900, "the browser network panel");
        hunt.measure("a third-party consent script", 1120, "the browser network panel");
        hunt.measure("the hero image", 620, "the browser network panel");
        hunt.split();

        // The server is innocent, and that is a measurement rather than a defence.
        hunt.ruleOut("waiting for the first byte (TTFB)", "180ms - the backend is not in this story");

        hunt.drillInto("the render-blocking JS bundle", "the browser coverage tab");
        hunt.measure("downloading 2.4MB over a mobile connection", 1500, "the coverage tab");
        hunt.measure("parsing and executing it", 400, "the coverage tab");
        hunt.split();

        hunt.confirm("2.4MB of render-blocking JavaScript, 78% of which this page never executes",
                "the coverage tab shows 1.9MB unused, and the first paint waits for all of it");

        hunt.review();
        System.out.println("Half of the slow websites in the world have a perfectly fast backend.");
    }
}
