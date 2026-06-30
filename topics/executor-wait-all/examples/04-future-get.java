import visual.VisualTaskBatch;

public class Playground {
    public static void main(String[] args) {
        VisualTaskBatch scene = new VisualTaskBatch("future-get");
        var executor = scene.fixedExecutor("workerPool", 1);

        var price = executor.submitFuture("load-price", 149);
        price.get();

        executor.completeAll();
        price.get();
    }
}
