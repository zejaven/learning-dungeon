import visual.VisualWebSocket;

public class Playground {
    public static void main(String[] args) {
        // registerHandler(new ChatHandler(), "/ws/chat"): a Spring WebSocketHandler
        // is a singleton bean, so ONE object handles every connection.
        VisualWebSocket server = VisualWebSocket
                .shared("chat.example.com", "/ws/chat", "ChatHandler");

        server.connect("tab-1");
        server.rememberInField("tab-1", "user", "ada");

        server.connect("tab-2");
        server.rememberInField("tab-2", "user", "omar");

        // Character for character the same code as the per-connection example.
        System.out.println("tab-1 is handled as: " + server.readField("tab-1", "user"));

        server.report();
        System.out.println("Nothing threw. It just answers as the wrong person.");
    }
}
