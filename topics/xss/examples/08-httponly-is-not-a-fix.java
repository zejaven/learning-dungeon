import visual.VisualXss;
import visual.VisualXss.Sink;

public class Playground {
    public static void main(String[] args) {
        // The session cookie is marked HttpOnly, so document.cookie cannot see
        // it. Worth doing -- and notice what it does not change.
        VisualXss site = VisualXss.site().httpOnlySession();

        site.reflect(Sink.HTML_TEXT, "<script>steal(document.cookie)</script>");

        site.report();
        System.out.println("The cookie stayed put. The attacker's script still ran on your origin,");
        System.out.println("and the browser attaches that cookie to every request it makes.");
    }
}
