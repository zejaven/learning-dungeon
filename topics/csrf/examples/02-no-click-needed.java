import visual.VisualCsrf;
import visual.VisualCsrf.Delivery;

public class Playground {
    public static void main(String[] args) {
        VisualCsrf bank = VisualCsrf.bank();

        // The bank still exposes a legacy GET /transfer. That single fact turns
        // an <img> tag into a complete attack: no form, no JavaScript, no click.
        bank.crossSiteAttempt(Delivery.IMAGE_TAG, 750);

        // Making GET read-only closes that door for good.
        bank.postOnly();
        bank.crossSiteAttempt(Delivery.IMAGE_TAG, 750);

        // And does nothing whatsoever about a cross-site form, which is allowed
        // to POST anywhere. "We only accept POST" is not a CSRF defence.
        bank.crossSiteAttempt(Delivery.AUTO_FORM, 750);

        bank.report();
        System.out.println("Safe methods matter -- and they are not the fix.");
    }
}
