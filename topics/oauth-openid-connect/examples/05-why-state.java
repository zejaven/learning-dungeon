import visual.VisualOAuth;
import visual.VisualOAuth.Tokens;

public class Playground {
    public static void main(String[] args) {
        // Nothing is stolen here. The attacker logs in as THEMSELVES, keeps
        // their own code, and gets the victim's browser to deliver it.
        VisualOAuth guarded = VisualOAuth.openIdConnect();
        guarded.authorize("alice", "photos.read");
        guarded.exchange(guarded.injectedCode());
        guarded.report();

        // The same trick against a client that never sends or checks `state`.
        VisualOAuth open = VisualOAuth.openIdConnect().withoutStateCheck();
        open.authorize("alice", "photos.read");
        Tokens tokens = open.exchange(open.injectedCode());

        // alice is at the browser; the app is now holding mallory's account.
        open.callApi("/photos", tokens, "photos.read");
        open.report();

        System.out.println("state proves the callback belongs to a flow you started.");
    }
}
