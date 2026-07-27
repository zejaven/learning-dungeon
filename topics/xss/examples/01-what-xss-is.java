import visual.VisualXss;
import visual.VisualXss.Sink;

public class Playground {
    public static void main(String[] args) {
        // A search page that prints the query back: "No results for <query>".
        // Nothing is escaped -- which is exactly what string concatenation,
        // an unescaped template and innerHTML all do by default.
        VisualXss site = VisualXss.site();

        // A normal user types a normal thing. The page shows it. No drama.
        site.reflect(Sink.HTML_TEXT, "hiking boots");

        // The attacker types markup instead of a word. Same endpoint, same
        // template, same code path -- but the browser reads this one as an
        // element rather than as text, and elements are allowed to run.
        site.reflect(Sink.HTML_TEXT, "<script>steal(document.cookie)</script>");

        site.report();
        System.out.println("Same code, two inputs, and only one of them stayed data.");
    }
}
