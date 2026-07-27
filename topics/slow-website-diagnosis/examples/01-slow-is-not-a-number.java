import visual.VisualLatencyHunt;

public class Playground {
    public static void main(String[] args) {
        VisualLatencyHunt hunt = VisualLatencyHunt.reported(
                "shop.example.com", "the product page", "the site is slow");

        // The move almost everybody makes first: an optimisation chosen from memory.
        hunt.guess("rewrite the product mapper and put a cache in front of it");

        // The move that actually shortens the hunt. Five questions, asked together.
        hunt.clarify("which page?", "GET /products/{id} - not 'the site'");
        hunt.clarify("who sees it?", "everyone, mobile and desktop alike");
        hunt.clarify("how slow?", "3-4 seconds; it used to be under one");
        hunt.clarify("since when?", "gradually over the last six weeks");
        hunt.clarify("how often?", "every time, not one request in ten");

        // "Fast enough" is a number somebody agrees to, not a feeling.
        hunt.target("the product page at p95", 800);

        // And latency has a shape, not an average.
        hunt.distribution(1400, 900, 3100, 5200);

        hunt.review();
        System.out.println("Same complaint, now with a page, a percentile and a target.");
    }
}
