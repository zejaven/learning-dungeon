import visual.VisualPushChannel;

public class Playground {
    public static void main(String[] args) {
        // Same protocol as polling, opposite side does the waiting: the client
        // asks once and the server simply does not answer until it has news.
        VisualPushChannel channel = VisualPushChannel.longPolling("dashboard", 8);

        channel.tick(3);

        // The parked request answers the instant the event exists.
        channel.serverEvent("order-42 paid");

        // Quiet stretches still cost something: the hold has to expire before a
        // proxy kills the connection, and the client re-opens straight away.
        channel.tick(8);
        channel.tick(8);

        channel.report();
        System.out.println("Long polling moves the waiting from the client to the server.");
    }
}
