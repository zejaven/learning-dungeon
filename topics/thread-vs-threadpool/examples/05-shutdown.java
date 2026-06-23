import visual.VisualThreadPool;

public class Playground {
    public static void main(String[] args) {
        VisualThreadPool demo = new VisualThreadPool("shutdown-demo");
        var pool = demo.fixedPool("workerPool", 2, 2);

        pool.submit("accepted-1", () -> {
        });
        pool.submit("accepted-2", () -> {
        });
        pool.submit("queued-3", () -> {
        });

        pool.shutdown();
        pool.submit("late-4", () -> {
        });

        pool.completeAll();
    }
}
