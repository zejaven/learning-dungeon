import visual.VisualCsrf;
import visual.VisualCsrf.Delivery;

public class Playground {
    public static void main(String[] args) {
        // The synchronizer token: an unpredictable value the server puts into
        // its own pages and demands back on every state-changing request.
        VisualCsrf bank = VisualCsrf.bank().csrfToken();

        // The bank's own page could read the token, because it IS the bank.
        bank.userTransfers(200);

        // The attacker's page cannot: reading the token would mean reading the
        // bank's HTML, and the same-origin policy forbids exactly that.
        bank.crossSiteAttempt(Delivery.AUTO_FORM, 1000);

        bank.report();
        System.out.println("The session proves who. The token proves the page that asked.");
    }
}
