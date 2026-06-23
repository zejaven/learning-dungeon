import visual.VisualThreadPool;

public class Playground {
    public static void main(String[] args) {
        VisualThreadPool demo = new VisualThreadPool("rejection-demo");
        var pool = demo.fixedPool("smallPool", 1, 1);

        pool.submit("image-1", () -> {
        });
        pool.submit("image-2", () -> {
        });
        pool.submit("image-3", () -> {
        });

        pool.completeAll();
    }
}
