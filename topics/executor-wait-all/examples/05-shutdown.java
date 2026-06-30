import visual.VisualTaskBatch;

public class Playground {
    public static void main(String[] args) {
        VisualTaskBatch scene = new VisualTaskBatch("shutdown-after-wait");
        var executor = scene.fixedExecutor("workerPool", 1);

        var receipt = executor.submitFuture("build-receipt", "receipt-42");
        executor.completeAll();
        receipt.get();

        executor.shutdown();
    }
}
