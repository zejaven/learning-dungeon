import visual.VisualContextSwitch;

public class Playground {
    public static void main(String[] args) {
        VisualContextSwitch scheduler = new VisualContextSwitch("api-core");

        scheduler.addThread("request-A");
        scheduler.addThread("request-B");

        scheduler.dispatchNext();
        scheduler.runInstructions(3);
        scheduler.expireTimeSlice();
        scheduler.runInstructions(2);
    }
}
