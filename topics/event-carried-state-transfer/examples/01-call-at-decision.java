import visual.VisualRateFeed;

public class Playground {
    public static void main(String[] args) {
        // The rate belongs to another service. The most obvious thing to do is
        // ask for it at the exact moment the decision needs it.
        VisualRateFeed feed = VisualRateFeed.callingOnEveryDecision();

        // rate-service knows the current rate. Nobody is told about it: whoever
        // needs the value has to come and ask for it.
        feed.publishRate("EUR/USD", 10800);

        // Checkout: 200.00 EUR. The decision blocks until the answer arrives.
        feed.price("EUR/USD", 20000);

        // A second checkout is a second call. The rate did not change, but the
        // decision point has no way to know that.
        feed.price("EUR/USD", 5000);

        feed.report();
        System.out.println("Always current — and checkout now needs a second service to be up.");
    }
}
