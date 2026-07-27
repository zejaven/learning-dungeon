import visual.VisualLongPoll;

public class Playground {
    public static void main(String[] args) {
        // The same hold, written asynchronously: the handler returns a
        // DeferredResult and the worker goes straight back to the pool.
        VisualLongPoll server = VisualLongPoll.heldAsync("GET /api/messages", 3, 20);

        server.poll("tab-1");
        server.poll("tab-2");
        server.poll("tab-3");

        // Still three workers — and now the fourth and fifth clients get in.
        server.poll("tab-4");
        server.poll("tab-5");

        server.tick(4);
        server.serverEvent("msg-1 for everyone");

        server.report();
        System.out.println("Held request, released thread: that is what makes long polling scale.");
    }
}
