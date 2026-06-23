import visual.VisualContextSwitch;

public class Playground {
    public static void main(String[] args) {
        VisualContextSwitch scheduler = new VisualContextSwitch("io-core");

        scheduler.addThread("http-handler");
        scheduler.addThread("metrics-flusher");

        scheduler.dispatchNext();
        scheduler.runInstructions(2);
        scheduler.blockForIo("database");
        scheduler.runInstructions(1);
        scheduler.wake("http-handler");
    }
}
