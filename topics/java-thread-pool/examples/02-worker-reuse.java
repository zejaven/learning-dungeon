import visual.VisualThreadPool;

public class Playground {
    public static void main(String[] args) {
        VisualThreadPool scene = new VisualThreadPool("worker-reuse");
        var pool = scene.fixedPool("singleWorkerPool", 1, 2);

        pool.submit("warm-cache", () -> {
        });
        pool.completeOne();

        pool.submit("serve-request", () -> {
        });
        pool.completeAll();
    }
}
