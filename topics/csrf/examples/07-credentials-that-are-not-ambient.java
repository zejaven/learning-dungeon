import visual.VisualCsrf;
import visual.VisualCsrf.Delivery;

public class Playground {
    public static void main(String[] args) {
        // The session leaves the cookie and becomes a header the application's
        // own JavaScript attaches to its own calls.
        VisualCsrf api = VisualCsrf.bank().bearerToken();

        // The application still works: it adds the credential deliberately.
        api.userTransfers(200);

        // The attacker's page can still cause the request -- and there is
        // nothing for the browser to attach, so it arrives as nobody.
        api.crossSiteAttempt(Delivery.AUTO_FORM, 1000);

        api.report();
        System.out.println("CSRF needs ambient authority. Remove that and it stops existing.");
    }
}
