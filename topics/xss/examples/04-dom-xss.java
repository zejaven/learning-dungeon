import visual.VisualXss;
import visual.VisualXss.Sink;

public class Playground {
    public static void main(String[] args) {
        // Server-side encoding is switched on and it is correct.
        VisualXss site = VisualXss.site().encodeForContext();

        // Anything the server renders is safe. This is the part that gets
        // audited, and it passes.
        site.reflect(Sink.HTML_TEXT, "<img src=x onerror=steal(document.cookie)>");

        // Now the page's own JavaScript reads location.hash and assigns it to
        // innerHTML. The fragment after '#' is never sent to the server, so the
        // access log, the template encoder and the WAF all see a normal visit.
        site.domRender("<img src=x onerror=steal(document.cookie)>");

        site.report();
        System.out.println("Nothing to find in the server logs -- the payload never went there.");
    }
}
