import visual.VisualJvmPipeline;

public class Playground {
    public static void main(String[] args) {
        VisualJvmPipeline pipeline = new VisualJvmPipeline("Main");

        pipeline.compile();
        pipeline.load();
        pipeline.verify();
        pipeline.initialize();

        // The interpreter starts with bytecode instructions for main().
        pipeline.interpret("main");
        pipeline.print("application started");
    }
}
