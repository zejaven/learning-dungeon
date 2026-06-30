import visual.VisualTaskBatch;

public class Playground {
    public static void main(String[] args) {
        VisualTaskBatch scene = new VisualTaskBatch("countdown-latch");
        var executor = scene.fixedExecutor("workerPool", 2);
        var done = scene.countDownLatch("allDone", 3);

        executor.submit("load-user", () -> done.countDown("load-user"));
        executor.submit("load-orders", () -> done.countDown("load-orders"));
        executor.submit("load-recommendations", () -> done.countDown("load-recommendations"));

        done.await();
        executor.completeAll();
    }
}
