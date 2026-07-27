import visual.VisualOAuth;
import visual.VisualOAuth.Grant;
import visual.VisualOAuth.Tokens;

public class Playground {
    public static void main(String[] args) {
        // A mobile app: a PUBLIC client, because anything shipped to a device
        // can be read out of it. It has no client_secret to fall back on.
        VisualOAuth safe = VisualOAuth.openIdConnect().publicClient();
        Grant code = safe.approve(safe.authorize("alice", "photos.read"));

        // The code travelled through the browser, so assume it was seen.
        Grant stolen = safe.stealTheCode(code);
        safe.redeemStolenCode(stolen);

        // The real client still has the one thing that never left it.
        Tokens tokens = safe.exchange(code);

        // And a code is good exactly once, even for its rightful owner.
        safe.exchange(code);
        safe.report();

        // The same theft against the same app with PKCE turned off.
        VisualOAuth exposed = VisualOAuth.openIdConnect().publicClient().withoutPkce();
        Grant code2 = exposed.approve(exposed.authorize("alice", "photos.read"));
        exposed.redeemStolenCode(exposed.stealTheCode(code2));
        exposed.report();

        System.out.println("PKCE binds the code to whoever asked for it.");
    }
}
