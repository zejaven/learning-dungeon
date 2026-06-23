import visual.VisualThreadPool;

public class Playground {
    public static void main(String[] args) {
        VisualThreadPool scene = new VisualThreadPool("queue-backpressure");
        var pool = scene.fixedPool("paymentPool", 1, 2);

        pool.submit("payment-1", () -> {
        });
        pool.submit("payment-2", () -> {
        });
        pool.submit("payment-3", () -> {
        });

        pool.completeAll();
    }
}
