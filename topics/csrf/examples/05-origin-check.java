import visual.VisualCsrf;
import visual.VisualCsrf.Delivery;

public class Playground {
    public static void main(String[] args) {
        // No token to store anywhere: the server just compares the Origin
        // header the browser sets with its own name.
        VisualCsrf api = VisualCsrf.bank().checkOrigin();

        // The session is valid, the user is real, and the request is still
        // refused -- because "who sent it" and "where it was built" differ.
        api.crossSiteAttempt(Delivery.AUTO_FORM, 1000);

        // The same check waves the bank's own page through.
        api.userTransfers(200);

        api.report();
        System.out.println("A header the page cannot set is a header worth checking.");
    }
}
