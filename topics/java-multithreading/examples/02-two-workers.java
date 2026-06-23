import visual.VisualThread;

public class Playground {
    public static void main(String[] args) {
        VisualThread demo = new VisualThread("post-office");

        Runnable sortLetters = demo.runnable("sortLetters", () ->
                System.out.println("Sorting one tray of letters"));

        Thread morningClerk = demo.thread("morning-clerk", sortLetters);
        Thread eveningClerk = demo.thread("evening-clerk", sortLetters);

        demo.start(morningClerk);
        demo.start(eveningClerk);
    }
}
