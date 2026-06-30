import visual.VisualThreadStop;

public class Playground {
    public static void main(String[] args) {
        VisualThreadStop demo = new VisualThreadStop("running-loop");

        demo.createWorker("metrics-poller");
        demo.start("metrics-poller");
        demo.work("metrics-poller", 1);

        demo.interrupt("metrics-poller");
        demo.work("metrics-poller", 1); // The loop still has to check interrupt status.
        demo.observeInterruptStatus("metrics-poller");
        demo.exit("metrics-poller");
        demo.join("metrics-poller");
    }
}
