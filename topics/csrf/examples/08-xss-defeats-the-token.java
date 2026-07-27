import visual.VisualCsrf;
import visual.VisualCsrf.Delivery;

public class Playground {
    public static void main(String[] args) {
        // Everything a CSRF review asks for is switched on.
        VisualCsrf bank = VisualCsrf.bank().csrfToken().sameSite("Strict");

        // A page on another domain gets nowhere.
        bank.crossSiteAttempt(Delivery.AUTO_FORM, 1000);

        // Now suppose the bank also has an XSS hole. The attacker's script runs
        // on the bank's own origin, so it reads the token off the page and its
        // request is same-site by definition. Every CSRF check agrees with it.
        bank.injectedScriptTransfer(1000);

        bank.report();
        System.out.println("A CSRF token is worth nothing on a site that has XSS.");
    }
}
