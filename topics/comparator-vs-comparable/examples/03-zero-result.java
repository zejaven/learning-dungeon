import visual.VisualOrdering;

import java.util.Comparator;

public class Playground {
    public static void main(String[] args) {
        Comparator<String> byLengthOnly = Comparator.comparingInt(String::length);

        VisualOrdering<String> ordering = VisualOrdering.usingComparator(
                "words",
                byLengthOnly,
                "word length only",
                "только длина слова");

        ordering.add("tea");
        ordering.add("jam");
        ordering.sameSortPosition("tea", "jam");
    }
}
