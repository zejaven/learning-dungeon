import visual.VisualAnonymousClass;

public class Playground {
    public static void main(String[] args) {
        VisualAnonymousClass visual = new VisualAnonymousClass("Runnable");
        visual.target("interface", "run()");

        Runnable task = new Runnable() {
            @Override
            public void run() {
                System.out.println("Send receipt");
            }
        };

        visual.created("task", task);
        task.run();
        visual.called("run()", "printed receipt");
    }
}
