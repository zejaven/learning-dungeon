import visual.VisualXss;
import visual.VisualXss.Sink;

public class Playground {
    public static void main(String[] args) {
        String payload = "<script>steal(document.cookie)</script>";

        // Encoding was forgotten on this one page. A Content-Security-Policy
        // does not fix that -- the injection still happens -- but the browser
        // refuses to run script it did not get from an allowed source.
        VisualXss guarded = VisualXss.site().contentSecurityPolicy("script-src 'self'");
        guarded.reflect(Sink.HTML_TEXT, payload);
        guarded.report();

        // The same policy with 'unsafe-inline' added, which is how most CSPs
        // get "fixed" when they break an inline onclick somewhere. An injected
        // script is inline script, so this policy protects nothing.
        VisualXss legacy = VisualXss.site()
                .contentSecurityPolicy("script-src 'self' 'unsafe-inline'");
        legacy.reflect(Sink.HTML_TEXT, payload);
        legacy.report();

        System.out.println("A safety net is only a net while it has no hole in it.");
    }
}
