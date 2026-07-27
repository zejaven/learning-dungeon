import visual.VisualOAuth;
import visual.VisualOAuth.Tokens;

public class Playground {
    public static void main(String[] args) {
        // A nightly report job. There is no browser to redirect and no person
        // to ask, so the whole front half of the protocol disappears.
        VisualOAuth job = VisualOAuth.oauth2();
        Tokens tokens = job.clientCredentials("reports.read");

        // The app acts as ITSELF, not on anyone's behalf: no user, no id_token.
        job.callApi("/reports", tokens, "reports.read");

        // There is no refresh token either - when this one dies, the job simply
        // authenticates again with its own credentials.
        job.refresh(tokens);

        job.report();
        System.out.println("client_credentials: the client IS the resource owner.");
    }
}
