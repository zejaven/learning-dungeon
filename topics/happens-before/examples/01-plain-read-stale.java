import visual.VisualHappensBefore;

public class Playground {
    public static void main(String[] args) {
        VisualHappensBefore hb = new VisualHappensBefore("plain-note");

        hb.writePlain("Cook", "order", "ready");
        Object seen = hb.readPlain("Waiter", "order");

        System.out.println("Waiter saw order = " + seen);
    }
}
