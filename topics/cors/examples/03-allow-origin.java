import visual.VisualCors;
import visual.VisualCors.Policy;
import visual.VisualCors.Request;

public class Playground {
    public static void main(String[] args) {
        // The API answers with an origin it does not recognise. A CORS policy
        // that names the wrong origin is exactly as useful as no policy at all.
        VisualCors cors = VisualCors.browser("https://app.example.com", "https://api.example.com",
                Policy.allowOrigin("https://admin.example.com"));
        cors.send(Request.get("/orders"));

        // One header, sent by the server, changes the verdict. Note who grants
        // the permission: never the client, always the resource being read.
        cors.reconfigure(Policy.allowOrigin("https://app.example.com"));
        cors.send(Request.get("/orders"));

        cors.report();
        System.out.println("Access-Control-Allow-Origin is the server saying who may read this.");
    }
}
