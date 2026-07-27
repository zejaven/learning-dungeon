import visual.VisualWebSocket;

public class Playground {
    public static void main(String[] args) {
        // Three connections, three ways of being served — the answer to
        // "is the endpoint per connection or shared?" is "it depends, and you check".
        VisualWebSocket.compareInstancing();

        System.out.println("Per-connection state on the Session is the answer that survives all three.");
    }
}
