import visual.VisualJvmPipeline;

public class Playground {
    public static void main(String[] args) {
        VisualJvmPipeline pipeline = new VisualJvmPipeline("OrderController");

        pipeline.compile();
        pipeline.load();
        pipeline.verify();
        pipeline.initialize();
        pipeline.interpret("handle");

        // Objects are allocated on the heap while bytecode is running.
        pipeline.allocateObject("Order");
        pipeline.print("orders=1");
    }
}
