import visual.VisualRateFeed;

public class Playground {
    public static void main(String[] args) {
        VisualRateFeed feed = VisualRateFeed.withEventCarriedState();
        feed.publishRate("EUR/USD", 10800);
        feed.price("EUR/USD", 20000);

        // A deploy, a pod eviction, a scale-out. The replica lived in memory.
        feed.restartInstance();

        // The new instance has never been told the rate, so it cannot decide.
        // Only new events would reach it, and rates change rarely.
        feed.price("EUR/USD", 20000);

        // The fix is that the stream is also a store: replaying the compacted
        // topic gives back the last value of every key. It reads the log, so it
        // works even while rate-service itself is unavailable.
        feed.rateServiceDown();
        feed.rebuildFromSnapshot();
        feed.price("EUR/USD", 20000);

        feed.report();
        System.out.println("A replica you cannot rebuild is a cache with extra steps.");
    }
}
