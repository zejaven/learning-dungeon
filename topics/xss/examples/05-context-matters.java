import visual.VisualXss;
import visual.VisualXss.Sink;

public class Playground {
    public static void main(String[] args) {
        // "We escape user input" -- meaning the four HTML characters & < > and
        // the double quote. The same escaper is now applied everywhere.
        VisualXss site = VisualXss.site().escapeAngleBrackets();

        // Between tags: right tool, right place. A tag cannot be opened.
        site.reflect(Sink.HTML_TEXT, "<script>steal(document.cookie)</script>");

        // In a quoted attribute: still fine, because the quote is escaped too.
        site.reflect(Sink.ATTRIBUTE, "\" onmouseover=steal(document.cookie) x=\"");

        // Inside a script block the metacharacter is the single quote, and this
        // escaper never touches it -- the payload closes the string and keeps
        // writing JavaScript.
        site.reflect(Sink.SCRIPT, "';steal(document.cookie);//");

        // In a link there is nothing to escape at all: javascript:... contains
        // no angle bracket and no quote. A URL needs its SCHEME checked.
        site.reflect(Sink.URL, "javascript:steal(document.cookie)");

        site.report();
        System.out.println("One escaper, four sinks, two holes.");
    }
}
