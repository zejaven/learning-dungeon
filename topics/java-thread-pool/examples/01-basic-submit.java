import visual.VisualThreadPool;

public class Playground {
    public static void main(String[] args) {
        VisualThreadPool scene = new VisualThreadPool("basic-submit");
        var pool = scene.fixedPool("apiPool", 2, 2);

        pool.submit("request-1", () -> {
        });
        pool.submit("request-2", () -> {
        });

        pool.completeAll();
    }
}
