import visual.VisualOAuth;
import visual.VisualOAuth.Tokens;

public class Playground {
    public static void main(String[] args) {
        VisualOAuth flow = VisualOAuth.openIdConnect();
        Tokens tokens = flow.exchange(flow.approve(flow.authorize("alice", "photos.read")));
        flow.callApi("/photos", tokens, "photos.read");

        // Nobody calls anything and nothing changes at the provider. Only the
        // clock moves, and the access token is finished.
        flow.advanceMinutes(VisualOAuth.ACCESS_LIFETIME_MINUTES + 1);
        flow.callApi("/photos", tokens, "photos.read");

        // The refresh token buys a new one on the back channel: no redirect, no
        // consent screen, no user. This is why 15 minutes is not a nuisance.
        Tokens fresh = flow.refresh(tokens);
        flow.callApi("/photos", fresh, "photos.read");

        flow.report();
        System.out.println("Short access token, long refresh token, back channel only.");
    }
}
