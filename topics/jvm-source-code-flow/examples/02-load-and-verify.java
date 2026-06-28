import visual.VisualJvmPipeline;

public class Playground {
    public static void main(String[] args) {
        VisualJvmPipeline pipeline = new VisualJvmPipeline("BillingJob");

        pipeline.compile();
        pipeline.load();

        // Verification happens before bytecode is trusted enough to execute.
        pipeline.verify();
    }
}
