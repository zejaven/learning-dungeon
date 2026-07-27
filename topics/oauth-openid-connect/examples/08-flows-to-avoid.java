import visual.VisualOAuth;
import visual.VisualOAuth.Tokens;

public class Playground {
    public static void main(String[] args) {
        // The implicit flow: no code, no back channel, so the access token
        // itself comes back in the URL fragment.
        VisualOAuth implicitFlow = VisualOAuth.oauth2();
        Tokens exposed = implicitFlow.implicitFlow("alice", "photos.read");
        implicitFlow.callApi("/photos", exposed, "photos.read");
        implicitFlow.report();

        // The password grant: the user types the provider password into the
        // app, which is the exact thing OAuth was invented to remove.
        VisualOAuth legacy = VisualOAuth.oauth2();
        legacy.passwordGrant("alice", "correct-horse-battery");
        legacy.report();

        System.out.println("Both are removed in OAuth 2.1: use code + PKCE instead.");
    }
}
