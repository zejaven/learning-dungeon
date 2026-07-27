import visual.VisualPageLoad;

public class Playground {
    public static void main(String[] args) {
        // Typing a plain http:// URL to a site that only serves https.
        VisualPageLoad plain = VisualPageLoad.browser();
        plain.type("http://shop.example.com");
        plain.resolve();
        plain.connect();
        plain.request();

        // Everything above was spent to receive one header: "go to https".
        plain.redirect("https://shop.example.com/");
        plain.resolve();
        plain.connect();
        plain.secure();
        plain.request();
        plain.respond();
        plain.report();

        // That response carried Strict-Transport-Security, so the next visit
        // rewrites the scheme locally: no plaintext request, no 301, no retry.
        VisualPageLoad remembered = VisualPageLoad.browser().hstsPreloaded("shop.example.com");
        remembered.type("http://shop.example.com");
        remembered.resolve();
        remembered.connect();
        remembered.secure();
        remembered.request();
        remembered.respond();
        remembered.report();

        System.out.println("A redirect is a whole round trip spent on an instruction to start over.");
    }
}
