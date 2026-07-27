import visual.VisualCsrf;
import visual.VisualCsrf.Delivery;

public class Playground {
    public static void main(String[] args) {
        // A bank the user is already signed in to. The session is a cookie --
        // the default in every server-rendered application ever written.
        VisualCsrf bank = VisualCsrf.bank();

        // The request the user actually makes, on the bank's own page.
        bank.userTransfers(200);

        // The user now opens an unrelated page in another tab. It contains a
        // hidden form aimed at the bank and submits it. Nothing was typed into
        // the bank, nothing was clicked -- and the browser attaches the session
        // cookie anyway, because the request is addressed to the bank.
        bank.crossSiteAttempt(Delivery.AUTO_FORM, 1000);

        bank.report();
        System.out.println("Two requests the server cannot tell apart. The user asked for one.");
    }
}
