import visual.VisualWebSocket;

public class Playground {
    public static void main(String[] args) {
        // @ServerEndpoint("/ws/chat") on a POJO: the container instantiates the
        // class once per connection, which is the Jakarta WebSocket default.
        VisualWebSocket server = VisualWebSocket
                .perConnection("chat.example.com", "/ws/chat", "ChatEndpoint");

        server.connect("tab-1");
        server.rememberInField("tab-1", "user", "ada");

        server.connect("tab-2");
        server.rememberInField("tab-2", "user", "omar");

        // Two objects, two fields. The same source line stored two different values.
        System.out.println("tab-1 is handled as: " + server.readField("tab-1", "user"));
        System.out.println("tab-2 is handled as: " + server.readField("tab-2", "user"));

        server.report();
    }
}
