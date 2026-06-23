import visual.VisualThread;

public class Playground {
    public static void main(String[] args) {
        VisualThread demo = new VisualThread("shared-task");

        Runnable cleanupJob = demo.runnable("cleanWorkstation", () ->
                System.out.println("Cleaning one workstation"));

        Thread morningWorker = demo.thread("morning-worker", cleanupJob);
        Thread eveningWorker = demo.thread("evening-worker", cleanupJob);

        demo.start(morningWorker);
        demo.start(eveningWorker);
    }
}
