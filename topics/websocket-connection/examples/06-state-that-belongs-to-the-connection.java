import visual.VisualWebSocket;

public class Playground {
    public static void main(String[] args) {
        // Same shared singleton handler as before — one object for everyone.
        VisualWebSocket server = VisualWebSocket
                .shared("chat.example.com", "/ws/chat", "ChatHandler");

        server.connect("tab-1");
        // session.getAttributes() in Spring, session.getUserProperties() in Jakarta.
        // The container creates a Session per connection in BOTH models.
        server.rememberInSession("tab-1", "user", "ada");

        server.connect("tab-2");
        server.rememberInSession("tab-2", "user", "omar");

        // Fan-out is a loop over sockets you keep yourself; the protocol has no rooms.
        server.broadcast("ada: hello everyone");

        server.report();
        System.out.println("Handler stateless, state on the session: correct in either instancing model.");
    }
}
