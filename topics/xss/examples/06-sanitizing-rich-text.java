import visual.VisualXss;
import visual.VisualXss.Sink;

public class Playground {
    public static void main(String[] args) {
        // Sometimes the value has to stay HTML: a review with bold text, a
        // formatted description. Encoding would show the tags instead of
        // applying them, so this is the one case for a sanitizer.
        VisualXss site = VisualXss.site().sanitizeRichText();

        // The allowlist keeps <b>. Everything it was not told to keep goes.
        site.save("comment", "<b>Great product</b><script>steal(document.cookie)</script>");
        site.showSaved(Sink.HTML_TEXT, "comment");

        // Dropping the tag is not enough on its own: the attribute is the
        // dangerous part here, and the sanitizer has to strip that too.
        site.save("bio", "<img src=x onerror=steal(document.cookie)>");
        site.showSaved(Sink.HTML_TEXT, "bio");

        site.report();
        System.out.println("Formatting survived; the executable parts did not.");
    }
}
