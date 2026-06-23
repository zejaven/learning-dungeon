import visual.VisualThread;

public class Playground {
    public static void main(String[] args) {
        VisualThread demo = new VisualThread("legacy-counter");

        Thread worker = demo.threadSubclass("legacy-worker", () ->
                System.out.println("Work is embedded inside the Thread subclass"));

        demo.start(worker);
    }
}
