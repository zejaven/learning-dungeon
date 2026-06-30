import visual.VisualRuntimeAreas;

public class Playground {
    public static void main(String[] args) {
        VisualRuntimeAreas jvm = new VisualRuntimeAreas();

        // A worker starts: a new stack appears (2 stacks now).
        jvm.startThread("worker-1");
        jvm.allocate("worker-1", "Task");

        // When the worker finishes, its WHOLE stack is discarded (back to 1 stack)...
        jvm.endThread("worker-1");

        // ...but the object it created still sits in the one shared heap, until the
        // garbage collector reclaims it once nothing references it anymore.
        System.out.println("live stacks after worker exits: " + jvm.stackCount());
    }
}
