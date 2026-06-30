import visual.VisualThreadStop;

public class Playground {
    public static void main(String[] args) {
        VisualThreadStop demo = new VisualThreadStop("blocked-worker");

        demo.createWorker("cache-refresher");
        demo.start("cache-refresher");
        demo.block("cache-refresher", "sleep()");

        demo.requestStop("cache-refresher");
        demo.interrupt("cache-refresher");
        demo.handleInterruptedException("cache-refresher");
        demo.exit("cache-refresher");
        demo.join("cache-refresher");
    }
}
