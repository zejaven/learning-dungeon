import visual.VisualHappensBefore;

public class Playground {
    public static void main(String[] args) {
        VisualHappensBefore hb = new VisualHappensBefore("wrong-lock");

        hb.lock("Writer", "kitchenLock");
        hb.writePlain("Writer", "dish", "soup");
        hb.unlock("Writer", "kitchenLock");

        hb.lock("Reader", "postOfficeLock");
        System.out.println("Reader dish = " + hb.readPlain("Reader", "dish"));
    }
}
