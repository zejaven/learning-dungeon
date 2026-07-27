import visual.VisualPushChannel;

public class Playground {
    public static void main(String[] args) {
        // One handshake, then no more requests and no more responses at all.
        VisualPushChannel channel = VisualPushChannel.webSocket("chat");

        channel.tick(2);
        channel.serverEvent("ada: hello");

        // The part only a socket gives you: the client answers on the same wire.
        channel.sendFromClient("omar: hi");

        channel.tick(2);
        channel.goOffline();
        channel.tick(1);
        channel.serverEvent("ada: are you there?");
        channel.tick(2);

        // Reconnecting is your own code, and a raw socket replays nothing.
        channel.goOnline();

        channel.report();
        System.out.println("A socket is symmetric; everything above it you write yourself.");
    }
}
