import visual.VisualHappensBefore;

public class Playground {
    public static void main(String[] args) {
        VisualHappensBefore hb = new VisualHappensBefore("thread-start");

        hb.writePlain("main", "config", "loaded");
        hb.startThread("main", "Worker");

        System.out.println("Worker config = " + hb.readPlain("Worker", "config"));
    }
}
