import visual.VisualRuntimeAreas;

public class Playground {
    public static void main(String[] args) {
        VisualRuntimeAreas jvm = new VisualRuntimeAreas();
        jvm.startThread("worker-1");

        // Calls on one thread push frames onto THAT thread's stack only.
        // main's stack and worker-1's stack are completely separate.
        jvm.call("main", "loadConfig");
        jvm.call("worker-1", "process");
        jvm.call("worker-1", "parse");

        // Returning pops a frame from just one stack; the other is untouched.
        jvm.ret("worker-1");
        jvm.ret("main");
    }
}
