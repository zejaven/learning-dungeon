import visual.VisualStream;

public class Playground {
    public static void main(String[] args) {
        // Intermediate operations are LAZY. Building a pipeline runs nothing:
        // peek would normally print each element, but with no terminal operation
        // it never fires. Notice there is no STREAM_TERMINAL_START event and no
        // element ever enters the pipeline.
        VisualStream.of("orders", 1, 2, 3)
                .peek("System.out.println", n -> System.out.println("seen " + n))
                .filter("n > 1", n -> n > 1)
                .map("n * 100", n -> n * 100);

        System.out.println("Pipeline built, but nothing ran (no terminal operation).");
    }
}
