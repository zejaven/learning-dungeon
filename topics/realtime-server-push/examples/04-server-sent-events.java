import visual.VisualPushChannel;

public class Playground {
    public static void main(String[] args) {
        // One GET whose response never ends. The server writes into it whenever
        // it has something to say.
        VisualPushChannel channel = VisualPushChannel.sse("dashboard");

        channel.tick(2);
        channel.serverEvent("order-42 paid");
        channel.tick(3);
        channel.serverEvent("order-42 packed");

        // The laptop lid closes and the stream dies.
        channel.goOffline();
        channel.tick(2);
        channel.serverEvent("order-42 shipped");
        channel.tick(2);

        // EventSource reconnects on its own and asks to resume from the last id
        // it saw, so the gap heals without any application code.
        channel.goOnline();

        // The one thing it cannot do: carry anything back up.
        channel.sendFromClient("mark as read");

        channel.report();
        System.out.println("SSE is one-way HTTP that reconnects and resumes by itself.");
    }
}
