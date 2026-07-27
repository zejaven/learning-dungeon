import visual.VisualOAuth;
import visual.VisualOAuth.Tokens;

public class Playground {
    public static void main(String[] args) {
        // Plain OAuth 2.0: the app asked for permission to read photos.
        VisualOAuth oauth = VisualOAuth.oauth2();
        Tokens keys = oauth.exchange(oauth.approve(oauth.authorize("alice", "photos.read")));

        // There is nothing here that names a user to this client.
        oauth.verifyIdToken(keys);

        // So the app guesses - and that guess is the most common OAuth bug.
        oauth.useAccessTokenAsLogin(keys);
        oauth.report();

        // OpenID Connect asks the same provider the other question: WHO is this?
        VisualOAuth oidc = VisualOAuth.openIdConnect();
        Tokens proper = oidc.exchange(oidc.approve(oidc.authorize("alice", "photos.read")));

        // Now there is a signed statement addressed to this client, and the
        // client checks its signature, issuer, audience, expiry and nonce.
        oidc.verifyIdToken(proper);
        oidc.report();

        System.out.println("OAuth answers 'may I'. OIDC answers 'who'.");
    }
}
