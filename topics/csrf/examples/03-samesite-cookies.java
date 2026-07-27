import visual.VisualCsrf;
import visual.VisualCsrf.Delivery;

public class Playground {
    public static void main(String[] args) {
        // SameSite=Lax is what browsers now apply to a cookie that says nothing.
        VisualCsrf bank = VisualCsrf.bank().sameSite("Lax");

        // A cross-site POST no longer carries the session cookie.
        bank.crossSiteAttempt(Delivery.AUTO_FORM, 1000);

        // A link the victim clicks is a top-level GET navigation, and Lax
        // deliberately allows those -- so this one still arrives authenticated.
        bank.crossSiteAttempt(Delivery.LINK_CLICK, 1000);

        // Strict closes that gap, at the price of arriving logged out from any
        // link outside the site.
        bank.sameSite("Strict");
        bank.crossSiteAttempt(Delivery.LINK_CLICK, 1000);

        // The user's own page is same-site, so it is unaffected either way.
        bank.userTransfers(200);

        bank.report();
        System.out.println("One cookie attribute, enforced by the browser before the request leaves.");
    }
}
