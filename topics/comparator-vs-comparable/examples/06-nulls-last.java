import visual.VisualOrdering;

import java.util.Comparator;

public class Playground {
    public static void main(String[] args) {
        Comparator<String> names = Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER);

        VisualOrdering<String> ordering = VisualOrdering.usingComparator(
                "names",
                names,
                "case-insensitive names, nulls last",
                "имена без учёта регистра, null в конце");

        ordering.add("delta");
        ordering.add(null);
        ordering.add("Alpha");
        ordering.sort();
    }
}
