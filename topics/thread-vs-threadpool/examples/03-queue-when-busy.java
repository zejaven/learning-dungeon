import visual.VisualThreadPool;

public class Playground {
    public static void main(String[] args) {
        VisualThreadPool demo = new VisualThreadPool("queue-demo");
        var pool = demo.fixedPool("singleWorkerPool", 1, 2);

        pool.submit("order-1", () -> {
        });
        pool.submit("order-2", () -> {
        });
        pool.submit("order-3", () -> {
        });

        pool.completeAll();
    }
}
