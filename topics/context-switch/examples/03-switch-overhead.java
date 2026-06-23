import visual.VisualContextSwitch;

public class Playground {
    public static void main(String[] args) {
        VisualContextSwitch scheduler = new VisualContextSwitch("busy-core");

        scheduler.addThread("parser");
        scheduler.addThread("compressor");

        scheduler.dispatchNext();
        for (int i = 0; i < 3; i++) {
            scheduler.runInstructions(1);
            scheduler.expireTimeSlice();
        }
    }
}
