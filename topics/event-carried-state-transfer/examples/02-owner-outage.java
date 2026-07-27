import visual.VisualRateFeed;

public class Playground {
    public static void main(String[] args) {
        VisualRateFeed feed = VisualRateFeed.callingOnEveryDecision();
        feed.publishRate("EUR/USD", 10800);

        // While everything is up, the design looks perfect.
        feed.price("EUR/USD", 20000);

        // rate-service is redeployed, throttled, or simply slow.
        feed.rateServiceDown();

        // The pricing service is healthy. Its database is healthy. Checkout is
        // down, because it borrowed the availability of a service it does not own.
        feed.price("EUR/USD", 20000);

        feed.report();
        System.out.println("A synchronous dependency multiplies availability: 99.9% x 99.9% is not 99.9%.");
    }
}
