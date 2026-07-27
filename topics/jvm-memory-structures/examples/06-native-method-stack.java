import visual.VisualMemoryAreas;

public class Playground {
    public static void main(String[] args) {
        VisualMemoryAreas jvm = new VisualMemoryAreas();

        // A normal Java call gets a frame on main's JVM stack.
        jvm.call("main", "readFile");

        // A native (JNI) call is recorded on main's NATIVE method stack instead:
        // a second, separate per-thread area.
        jvm.callNative("main", "read0");

        // Another thread gets its own JVM stack AND its own native method stack.
        jvm.startThread("worker-1");
        jvm.callNative("worker-1", "currentTimeMillis");

        System.out.println("per thread: 1 JVM stack + 1 PC register + 1 native method stack");
    }
}
