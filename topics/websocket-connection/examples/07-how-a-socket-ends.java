import visual.VisualWebSocket;

public class Playground {
    public static void main(String[] args) {
        VisualWebSocket server = VisualWebSocket
                .perConnection("chat.example.com", "/ws/chat", "ChatEndpoint");

        server.connect("tab-1");
        server.connect("tab-2");
        server.push("tab-1", "your order shipped");

        // Closing is a handshake too: a CLOSE frame each way, then the TCP FIN.
        server.close("tab-1", 1000, "normal closure");

        // A tunnel, a dead battery, a NAT timeout: no CLOSE frame, code 1006.
        server.drop("tab-2");

        // The socket is gone, so this frame is gone. No queue, no retry, no replay.
        server.push("tab-2", "your order was delivered");

        server.report();
    }
}
