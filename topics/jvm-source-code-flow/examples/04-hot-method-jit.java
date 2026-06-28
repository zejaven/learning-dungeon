import visual.VisualJvmPipeline;

public class Playground {
    public static void main(String[] args) {
        VisualJvmPipeline pipeline = new VisualJvmPipeline("PriceCalculator");

        pipeline.compile();
        pipeline.load();
        pipeline.verify();
        pipeline.initialize();

        // A method that is called often enough becomes hot and gets JIT-compiled.
        pipeline.callHotMethod("total", 12);
        pipeline.print("total=42");
    }
}
