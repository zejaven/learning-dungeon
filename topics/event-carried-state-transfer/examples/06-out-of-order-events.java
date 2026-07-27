import visual.VisualRateFeed;

public class Playground {
    public static void main(String[] args) {
        VisualRateFeed feed = VisualRateFeed.withEventCarriedState();

        feed.publishRate("EUR/USD", 10800);   // version 1
        feed.advanceSeconds(10);
        feed.publishRate("EUR/USD", 10950);   // version 2, applied over version 1

        // A broker rebalance replays an older event. Applying it would overwrite
        // a newer rate with an older one, so the version check drops it.
        feed.redeliver("EUR/USD", 1);

        // At-least-once delivery: the same event again. Applying it by version is
        // idempotent, so the duplicate changes nothing at all.
        feed.redeliver("EUR/USD", 2);

        // The replica still holds version 2.
        feed.price("EUR/USD", 20000);

        feed.report();
        System.out.println("Order and duplicates are the broker's business; the version is yours.");
    }
}
