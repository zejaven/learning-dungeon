import visual.VisualRateFeed;

public class Playground {
    public static void main(String[] args) {
        // A read-through cache: keep the answer for 60 seconds, then ask again.
        VisualRateFeed feed = VisualRateFeed.cachingLocally(60);
        feed.publishRate("EUR/USD", 10800);

        // The first decision pays for the call and fills the cache.
        feed.price("EUR/USD", 20000);

        // The next ones are local reads: no call, no waiting.
        feed.advanceSeconds(30);
        feed.price("EUR/USD", 20000);

        // rate-service moves the rate. The cache does not know and cannot know:
        // nothing pushes the change, and the TTL has not expired yet.
        feed.publishRate("EUR/USD", 10950);
        feed.price("EUR/USD", 20000);

        // Past the TTL the next decision refreshes and gets the new rate.
        feed.advanceSeconds(40);
        feed.price("EUR/USD", 20000);

        feed.report();
        System.out.println("A TTL is not a freshness guarantee — it is a bound on how wrong you may be.");
    }
}
