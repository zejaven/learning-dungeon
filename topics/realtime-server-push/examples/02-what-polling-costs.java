import visual.VisualPushChannel;

public class Playground {
    public static void main(String[] args) {
        // Same event, same 12 ticks, two polling intervals. There is no setting
        // that is good at both: the interval IS the trade-off.
        VisualPushChannel eager = VisualPushChannel.shortPolling("dashboard, every 3 ticks", 3);
        eager.tick(1);
        eager.serverEvent("order-42 paid");
        eager.tick(11);
        eager.report();

        VisualPushChannel lazy = VisualPushChannel.shortPolling("dashboard, every 12 ticks", 12);
        lazy.tick(1);
        lazy.serverEvent("order-42 paid");
        lazy.tick(11);
        lazy.report();

        System.out.println("Fresh data or few requests: polling makes you pick one.");
    }
}
