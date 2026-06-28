import java.util.Comparator;
import visual.VisualSetComparison;

public class Playground {
    public static void main(String[] args) {
        VisualSetComparison<String> sets = new VisualSetComparison<>(
                "shelf codes",
                Comparator.comparingInt(String::length),
                "string length",
                "длина строки");

        sets.add("AA");
        sets.add("BB");
        sets.add("CCC");
        sets.showIterationOrder();
    }
}
