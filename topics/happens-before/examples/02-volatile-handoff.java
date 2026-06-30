import visual.VisualHappensBefore;

public class Playground {
    public static void main(String[] args) {
        VisualHappensBefore hb = new VisualHappensBefore("volatile-flag");

        hb.writePlain("Writer", "payload", "invoice-42");
        hb.writeVolatile("Writer", "ready", true);
        hb.readVolatile("Reader", "ready");

        System.out.println("Reader payload = " + hb.readPlain("Reader", "payload"));
    }
}
