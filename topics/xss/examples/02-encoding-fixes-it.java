import visual.VisualXss;
import visual.VisualXss.Sink;

public class Playground {
    public static void main(String[] args) {
        // The same page, with one thing changed: values are encoded on the way
        // out, using the encoder that matches where they are going.
        VisualXss site = VisualXss.site().encodeForContext();

        // The classic payload. The angle brackets become entities, so the
        // browser builds a text node that happens to look like a tag.
        site.reflect(Sink.HTML_TEXT, "<script>steal(document.cookie)</script>");

        // No script tag needed: an event handler on any element is just as
        // executable. Encoding stops this one for the same reason.
        site.reflect(Sink.HTML_TEXT, "<img src=x onerror=steal(document.cookie)>");

        site.report();
        System.out.println("The user still sees exactly what was typed -- as text.");
    }
}
