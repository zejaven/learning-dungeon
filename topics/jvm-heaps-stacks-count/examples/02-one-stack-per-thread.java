import visual.VisualRuntimeAreas;

public class Playground {
    public static void main(String[] args) {
        // Start with one stack (main) and one heap.
        VisualRuntimeAreas jvm = new VisualRuntimeAreas();

        // Each thread that starts gets its OWN stack. The count of stacks grows
        // with the number of live threads: main + worker-1 + worker-2 = 3 stacks.
        jvm.startThread("worker-1");
        jvm.startThread("worker-2");

        // The heap, however, is still a single shared region — its count never changes.
        System.out.println("live stacks: " + jvm.stackCount());
    }
}
