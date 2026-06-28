import visual.VisualStringConcat;

public class Playground {
    public static void main(String[] args) {
        VisualStringConcat scene = new VisualStringConcat();

        // Same workload, two strategies. First the naive +, then StringBuilder.
        // Compare the "chars copied" and "garbage" counters in the DONE events:
        // the builder does a fraction of the work and leaves almost no garbage.
        scene.concatLoop("ab", 6);
        scene.builderLoop("ab", 6);

        System.out.println("Same result, very different cost.");
    }
}
