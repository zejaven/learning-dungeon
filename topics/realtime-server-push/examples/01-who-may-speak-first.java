import visual.VisualPushChannel;

public class Playground {
    public static void main(String[] args) {
        // An operations dashboard has to show a payment the moment it clears.
        // Start with the only thing plain HTTP offers: ask again on a timer.
        VisualPushChannel channel = VisualPushChannel.shortPolling("dashboard", 6);

        channel.tick(2);

        // The event happens on the server. Nobody asked for it, and the server
        // has no way to dial the tab: a browser has no address to be called on.
        channel.serverEvent("order-42 paid");

        // So it waits. The next timer tick is what finally carries it across.
        channel.tick(4);

        // And the poll after that pays for a full round trip to learn nothing.
        channel.tick(6);

        channel.report();
        System.out.println("Only the client may start an HTTP conversation.");
    }
}
