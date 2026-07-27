import visual.VisualLongPoll;

public class Playground {
    public static void main(String[] args) {
        // Same URL, same method, same status codes. One rule changed: while there
        // is nothing to report, the handler does not return a response.
        VisualLongPoll server = VisualLongPoll.held("GET /api/messages", 4, 10);

        server.poll("tab-1");

        server.tick(3);

        // The parked request is completed the instant the event exists.
        server.serverEvent("msg-1 from ada");

        // The response ENDED that request, so the client opens the next one.
        server.poll("tab-1");

        server.tick(2);
        server.serverEvent("msg-2 from omar");

        server.report();
        System.out.println("Long polling moves the waiting from the client to the server.");
    }
}
