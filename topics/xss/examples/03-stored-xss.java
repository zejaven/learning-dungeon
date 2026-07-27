import visual.VisualXss;
import visual.VisualXss.Sink;

public class Playground {
    public static void main(String[] args) {
        VisualXss site = VisualXss.site();

        // The attacker posts a product review once and goes away. Saving the
        // string is not the bug: a database column full of angle brackets
        // harms nobody.
        site.save("comment", "<script>steal(document.cookie)</script>");

        // The bug happens on the way out, once per reader -- and the readers
        // did nothing except open a page of a site they already trusted.
        site.showSaved(Sink.HTML_TEXT, "comment");
        site.showSaved(Sink.HTML_TEXT, "comment");

        site.report();
        System.out.println("One submission, one victim per page view.");
    }
}
