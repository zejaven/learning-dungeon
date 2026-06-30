import visual.VisualTaskBatch;

import java.util.List;

public class Playground {
    public static void main(String[] args) {
        VisualTaskBatch scene = new VisualTaskBatch("invoke-all");
        var executor = scene.fixedExecutor("workerPool", 2);

        executor.invokeAll(List.of(
                "load-profile",
                "load-orders",
                "load-discounts"
        ));
    }
}
