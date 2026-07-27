import visual.VisualWebSocket;

public class Playground {
    public static void main(String[] args) {
        // A WebSocket does not start as a WebSocket. It starts as one HTTP GET
        // carrying Upgrade: websocket, on a TCP connection the browser just opened.
        VisualWebSocket server = VisualWebSocket
                .perConnection("chat.example.com", "/ws/chat", "ChatEndpoint")
                .withOriginCheck("https://app.example.com");

        server.connect("tab-1");

        // Until the 101 arrives this is still HTTP, so it can still be refused
        // with a status code. After the 101 there is no status code left to send.
        server.connect("evil-tab", "https://evil.example.com");

        server.report();
        System.out.println("101 does not mean 'here is your answer' — it means 'stop speaking HTTP'.");
    }
}
