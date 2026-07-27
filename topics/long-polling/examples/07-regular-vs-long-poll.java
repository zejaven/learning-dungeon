import visual.VisualLongPoll;

public class Playground {
    public static void main(String[] args) {
        // One timeline — 24 ticks, 3 events — priced three ways: a regular
        // endpoint polled every 6 ticks, a blocking long poll, an async one.
        VisualLongPoll.compare();

        System.out.println("Long polling buys latency with server-side occupancy.");
    }
}
