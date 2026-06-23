import visual.VisualContextSwitch;

public class Playground {
    public static void main(String[] args) {
        VisualContextSwitch scheduler = new VisualContextSwitch("service-core");

        scheduler.addThread("batch-job");
        scheduler.addThread("health-check");

        scheduler.dispatchNext();
        scheduler.runInstructions(4);
        scheduler.finishRunning();
        scheduler.runInstructions(1);
    }
}
