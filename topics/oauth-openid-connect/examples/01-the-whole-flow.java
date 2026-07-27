import visual.VisualOAuth;
import visual.VisualOAuth.Grant;
import visual.VisualOAuth.Redirect;
import visual.VisualOAuth.Tokens;

public class Playground {
    public static void main(String[] args) {
        // "Log in with the provider, then print my photos." OpenID Connect is
        // OAuth 2.0 plus one extra thing in the response: an id_token.
        VisualOAuth flow = VisualOAuth.openIdConnect();

        // FRONT CHANNEL. The client cannot ask the provider anything on the
        // user's behalf yet, so it redirects the browser there instead. What it
        // wants is written in the URL, where everyone can read it.
        Redirect atProvider = flow.authorize("alice", "photos.read");

        // The password is typed on the PROVIDER's page, never in the app, and
        // the user is shown exactly what is being asked for.
        Grant code = flow.approve(atProvider);

        // BACK CHANNEL. Server to server, out of the browser's reach - and only
        // here do tokens come into existence.
        Tokens tokens = flow.exchange(code);

        // The id_token is the only thing in the response that says who the user
        // is, and it is worth nothing until the client validates it.
        flow.verifyIdToken(tokens);

        // The access token is addressed to the API and to nobody else.
        flow.callApi("/photos", tokens, "photos.read");

        flow.report();
        System.out.println("The app got the photos, and never saw the password.");
    }
}
