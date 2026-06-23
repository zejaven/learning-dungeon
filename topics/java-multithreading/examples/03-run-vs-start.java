import visual.VisualThread;

public class Playground {
    public static void main(String[] args) {
        VisualThread demo = new VisualThread("traffic-control");

        Runnable switchLight = demo.runnable("switchLight", () ->
                System.out.println("Traffic light changed"));

        Thread worker = demo.thread("signal-worker", switchLight);

        demo.callRunDirectly(worker);
        demo.start(worker);
    }
}
