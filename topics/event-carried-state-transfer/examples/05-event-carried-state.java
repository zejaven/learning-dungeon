import visual.VisualRateFeed;

public class Playground {
    public static void main(String[] args) {
        // Event-carried state transfer: rate-service publishes every change, and
        // this service keeps its own copy of what it needs.
        VisualRateFeed feed = VisualRateFeed.withEventCarriedState();

        // The event arrives before anyone asks for it. No decision waited.
        feed.publishRate("EUR/USD", 10800);

        // The decision is a local read: no call, no timeout, no circuit breaker.
        feed.price("EUR/USD", 20000);

        // The rate moves. The replica is updated by the stream, not by the
        // decision, so the next checkout is priced correctly without any work.
        feed.advanceSeconds(15);
        feed.publishRate("EUR/USD", 10950);
        feed.price("EUR/USD", 20000);

        feed.report();
        System.out.println("Zero calls inside the decision: the data was already here when it was needed.");
    }
}
