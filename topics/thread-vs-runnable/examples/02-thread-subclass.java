import visual.VisualThread;

public class Playground {
    public static void main(String[] args) {
        VisualThread demo = new VisualThread("legacy-style");

        Thread worker = demo.threadSubclass("legacy-worker", () ->
                System.out.println("Work lives inside the Thread subclass"));

        demo.start(worker);
    }
}
