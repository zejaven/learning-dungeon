import visual.VisualWebSocket;

public class Playground {
    public static void main(String[] args) {
        VisualWebSocket server = VisualWebSocket
                .perConnection("chat.example.com", "/ws/chat", "ChatEndpoint");

        server.connect("tab-1");

        // The client writes when it has something to say.
        server.send("tab-1", "hi");

        // And so does the server — three frames, no request asked for any of them.
        server.push("tab-1", "welcome, ada");
        server.push("tab-1", "omar joined");
        server.push("tab-1", "omar: hello");

        // Binary is a first-class opcode here, not base64 inside a text protocol.
        server.sendBinary("tab-1", "avatar.png", 4096);

        server.report();
        System.out.println("Four frames out, two in. There is no request/response pairing left to break.");
    }
}
