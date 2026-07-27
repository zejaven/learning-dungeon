import visual.VisualLongPoll;

public class Playground {
    public static void main(String[] args) {
        // Long polling written the obvious way: the handler blocks until it has
        // news, so a parked request keeps the worker thread that picked it up.
        VisualLongPoll server = VisualLongPoll.held("GET /api/messages", 3, 20);

        server.poll("tab-1");
        server.poll("tab-2");
        server.poll("tab-3");

        // Three workers, three clients doing nothing at all, and the pool is gone.
        server.poll("tab-4");

        server.tick(4);

        // One event answers every parked request at once.
        server.serverEvent("msg-1 for everyone");

        server.report();
        System.out.println("A blocking long poll spends a thread on every idle client.");
    }
}
