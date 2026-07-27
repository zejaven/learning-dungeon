import visual.VisualRateFeed;

public class Playground {
    public static void main(String[] args) {
        VisualRateFeed feed = VisualRateFeed.cachingLocally(60);
        feed.publishRate("EUR/USD", 10800);
        feed.price("EUR/USD", 20000);

        feed.rateServiceDown();
        feed.advanceSeconds(90);

        // The entry expired and the refresh reaches nobody. The cache serves the
        // expired copy anyway — stale-if-error, with no bound on "how stale".
        feed.price("EUR/USD", 20000);

        // Bound it: for money, say how old a rate may be and fail closed beyond that.
        feed.refuseStaleAfter(120);

        // Still inside the declared window: the expired copy is acceptable, and
        // the decision made on it is flagged so it can be found afterwards.
        feed.price("EUR/USD", 20000);

        // Past the window there is no acceptable answer left, so the decision is
        // refused rather than made on a rate already known to be wrong.
        feed.advanceSeconds(60);
        feed.price("EUR/USD", 20000);

        // When the owner comes back, the next decision refreshes normally.
        feed.rateServiceUp();
        feed.price("EUR/USD", 20000);

        feed.report();
        System.out.println("Stale-if-error is a policy you choose and bound, not one you inherit.");
    }
}
