import visual.VisualThread;

public class Playground {
    public static void main(String[] args) {
        VisualThread demo = new VisualThread("reporting");

        Runnable reportJob = demo.runnable("printReport", () ->
                System.out.println("Building a small report"));

        Thread worker = demo.thread("report-worker", reportJob);
        demo.start(worker);
    }
}
