import visual.VisualWebSocket;

public class Playground {
    public static void main(String[] args) {
        // wss:// is TLS between TCP and the frames — exactly where it sits under HTTP.
        VisualWebSocket server = VisualWebSocket
                .perConnection("chat.example.com", "/ws/chat", "ChatEndpoint")
                .secure();

        server.connect("tab-1");

        server.transportFacts();

        // A silent TCP connection can be dead for minutes without anyone noticing,
        // and every box in the path closes idle connections on its own schedule.
        server.ping("tab-1");

        server.report();
        System.out.println("One TCP connection, ordered and reliable. Never UDP.");
    }
}
