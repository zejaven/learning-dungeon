import visual.VisualLongPoll;

public class Playground {
    public static void main(String[] args) {
        // The endpoint every REST API is made of: the handler runs and the
        // response is written before the request leaves the worker thread.
        VisualLongPoll server = VisualLongPoll.immediate("GET /api/messages", 4);

        // Nothing has happened yet, so the answer is "nothing" — and it costs a
        // full round trip all the same.
        server.poll("tab-1");

        server.tick(3);

        // Now there IS news, and no unanswered request to write it into.
        server.serverEvent("msg-1 from ada");

        server.tick(3);

        // The client asks again and finally learns about it, three ticks late.
        server.poll("tab-1");

        server.report();
        System.out.println("A regular request is over the moment the handler returns.");
    }
}
