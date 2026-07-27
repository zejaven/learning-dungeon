import visual.VisualLongPoll;

public class Playground {
    public static void main(String[] args) {
        // A hold of 6 ticks: the server answers empty on purpose after that,
        // rather than waiting for a proxy to cut the connection for it.
        VisualLongPoll server = VisualLongPoll.held("GET /api/messages", 4, 6);

        server.poll("tab-1");
        server.tick(6);

        // Quiet again — the client re-opens and the hold expires a second time.
        server.poll("tab-1");
        server.tick(6);

        // The third request is the lucky one.
        server.poll("tab-1");
        server.tick(2);
        server.serverEvent("msg-1 from ada");

        server.report();
        System.out.println("An idle long poll costs one request per hold window per client.");
    }
}
