import visual.VisualThread;

public class Playground {
    public static void main(String[] args) {
        VisualThread demo = new VisualThread("run-vs-start");

        Runnable directJob = demo.runnable("directCallJob", () ->
                System.out.println("This body runs on the current thread"));
        Thread notStarted = demo.thread("not-started-worker", directJob);
        demo.callRunDirectly(notStarted);

        Runnable startedJob = demo.runnable("startedJob", () ->
                System.out.println("This body runs after start()"));
        Thread started = demo.thread("started-worker", startedJob);
        demo.start(started);
    }
}
