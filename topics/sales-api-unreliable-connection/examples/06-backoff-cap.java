import visual.VisualSaleRegistration;

public class Playground {
    public static void main(String[] args) {
        VisualSaleRegistration pos = new VisualSaleRegistration("pos-7");

        pos.recordSale("sale-1", "coffee", 250);

        // A long outage. Each failed attempt doubles the wait before the next
        // one: 200, 400, 800, 1600 ms ... so a fleet of tills does not hammer
        // the server the moment it comes back.
        for (int attempt = 0; attempt < 8; attempt++) {
            pos.attemptRequestLost("sale-1");
        }

        // The delay is capped, otherwise a long outage would push the retry
        // hours into the future. Real clients also add random jitter so the
        // whole fleet does not retry in lockstep.
        pos.attemptDelivered("sale-1");

        System.out.println("Exponential backoff, capped and jittered, keeps retries from becoming a storm.");
    }
}
