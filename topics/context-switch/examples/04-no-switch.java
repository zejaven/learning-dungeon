import visual.VisualContextSwitch;

public class Playground {
    public static void main(String[] args) {
        VisualContextSwitch scheduler = new VisualContextSwitch("single-thread-core");

        scheduler.addThread("image-resizer");
        scheduler.dispatchNext();
        scheduler.runInstructions(5);
        scheduler.runInstructions(5);

        System.out.println("A method call is not an OS context switch.");
    }
}
