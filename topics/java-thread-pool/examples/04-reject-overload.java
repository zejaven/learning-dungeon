import visual.VisualThreadPool;

public class Playground {
    public static void main(String[] args) {
        VisualThreadPool scene = new VisualThreadPool("reject-overload");
        var pool = scene.fixedPool("smallPool", 1, 1);

        pool.submit("job-1", () -> {
        });
        pool.submit("job-2", () -> {
        });
        pool.submit("job-3", () -> {
        });
    }
}
