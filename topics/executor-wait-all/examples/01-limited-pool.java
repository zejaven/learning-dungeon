import visual.VisualTaskBatch;

public class Playground {
    public static void main(String[] args) {
        VisualTaskBatch scene = new VisualTaskBatch("limited-pool");
        var executor = scene.fixedExecutor("workerPool", 2);

        executor.submit("resize-image");
        executor.submit("send-email");
        executor.submit("write-audit");

        executor.completeAll();
    }
}
