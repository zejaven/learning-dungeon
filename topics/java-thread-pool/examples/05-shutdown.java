import visual.VisualThreadPool;

public class Playground {
    public static void main(String[] args) {
        VisualThreadPool scene = new VisualThreadPool("shutdown");
        var pool = scene.fixedPool("workerPool", 1, 1);

        pool.submit("accepted-work", () -> {
        });
        pool.shutdown();
        pool.submit("late-work", () -> {
        });

        pool.completeAll();
    }
}
