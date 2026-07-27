import visual.VisualRateFeed;

public class Playground {
    public static void main(String[] args) {
        VisualRateFeed feed = VisualRateFeed.withEventCarriedState();
        feed.publishRate("EUR/USD", 10800);
        feed.price("EUR/USD", 20000);

        // The consumer stops receiving: a stuck listener, a lagging consumer
        // group, a partitioned broker. Nothing throws anywhere.
        feed.feedStops();

        // The rate moves by 6%. rate-service is fine and publishes it. The
        // pricing service never finds out.
        feed.publishRate("EUR/USD", 11500);
        feed.advanceSeconds(1800);

        // Half an hour later every checkout still succeeds, at the old rate.
        // This is the failure this topic exists for: it looks exactly like health.
        feed.price("EUR/USD", 20000);

        // Declare what "fresh enough" means and the same read becomes visible.
        feed.flagStaleAfter(120);
        feed.price("EUR/USD", 20000);

        // For a price, the usual answer is to fail closed instead.
        feed.refuseStaleAfter(120);
        feed.price("EUR/USD", 20000);

        // The listener is restarted and the backlog is delivered in order. Note
        // what that does and does not fix: the queued event is itself half an
        // hour old, so catching up is not the same as being current. Only a
        // freshly published rate makes the decision safe again.
        feed.feedResumes();
        feed.publishRate("EUR/USD", 11550);
        feed.price("EUR/USD", 20000);

        feed.report();
        System.out.println("Monitor the age of the data, not the error rate: staleness raises no exception.");
    }
}
