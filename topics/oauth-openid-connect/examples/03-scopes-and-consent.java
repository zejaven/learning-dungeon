import visual.VisualOAuth;
import visual.VisualOAuth.Grant;
import visual.VisualOAuth.Redirect;
import visual.VisualOAuth.Tokens;

public class Playground {
    public static void main(String[] args) {
        VisualOAuth flow = VisualOAuth.openIdConnect();

        // The API knows nothing about sessions or users - without a token there
        // is simply nothing to check.
        flow.callApi("/photos", null, "photos.read");

        // The app asks for two scopes; the user grants one of them.
        Redirect atProvider = flow.authorize("alice", "photos.read", "photos.delete");
        Grant code = flow.approveOnly(atProvider, "openid", "photos.read");
        Tokens tokens = flow.exchange(code);

        // Same token, two calls: what was granted works, what was not does not.
        flow.callApi("/photos", tokens, "photos.read");
        flow.callApi("/photos/17", tokens, "photos.delete");
        flow.report();

        // An app that asks for everything up front gets told no right here.
        VisualOAuth greedy = VisualOAuth.openIdConnect();
        greedy.deny(greedy.authorize("alice", "photos.delete", "contacts.read", "mail.send"));
        greedy.report();

        System.out.println("A valid token is not permission - the scopes are.");
    }
}
