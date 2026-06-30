import visual.VisualHappensBefore;

public class Playground {
    public static void main(String[] args) {
        VisualHappensBefore hb = new VisualHappensBefore("thread-join");

        hb.startThread("main", "Worker");
        hb.writePlain("Worker", "result", "done");
        hb.finishThread("Worker");
        hb.joinThread("main", "Worker");

        System.out.println("main result = " + hb.readPlain("main", "result"));
    }
}
