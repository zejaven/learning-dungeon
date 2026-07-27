import visual.VisualCsrf;
import visual.VisualCsrf.Delivery;

public class Playground {
    public static void main(String[] args) {
        VisualCsrf api = VisualCsrf.bank();

        // A JSON body is not a shape an HTML form can produce, so the browser
        // asks permission first -- and never sends the real request.
        api.crossSiteAttempt(Delivery.FETCH_JSON, 1000);

        // A form needs no permission at all. This is why "our API is JSON only"
        // must mean the server REFUSES form content types, not merely prefers JSON.
        api.crossSiteAttempt(Delivery.AUTO_FORM, 1000);

        // The configuration that reflects any Origin with credentials allowed
        // hands the browser's verdict back to the attacker.
        api.corsReflectsAnyOrigin();
        api.crossSiteAttempt(Delivery.FETCH_JSON, 1000);

        api.report();
        System.out.println("CORS decides who may ask. It does not decide who may act.");
    }
}
