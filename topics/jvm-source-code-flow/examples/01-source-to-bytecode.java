import visual.VisualJvmPipeline;

public class Playground {
    public static void main(String[] args) {
        // A .java file is readable source, not the format the JVM executes.
        VisualJvmPipeline pipeline = new VisualJvmPipeline("OrderService");

        // This models the common misconception: "the JVM runs source".
        pipeline.tryRunSource();

        // javac produces the .class bytecode artifact that the JVM can load.
        pipeline.compile();
    }
}
