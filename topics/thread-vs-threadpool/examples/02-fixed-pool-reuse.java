import visual.VisualThreadPool;

public class Playground {
    public static void main(String[] args) {
        VisualThreadPool demo = new VisualThreadPool("reuse-demo");
        var pool = demo.fixedPool("apiPool", 2, 2);

        pool.submit("request-1", () -> {
        });
        pool.submit("request-2", () -> {
        });

        pool.completeOne();
        pool.submit("request-3", () -> {
        });
        pool.completeAll();
    }
}
