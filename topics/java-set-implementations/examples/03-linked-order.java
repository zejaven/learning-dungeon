import visual.VisualSetComparison;

public class Playground {
    public static void main(String[] args) {
        VisualSetComparison<String> sets = new VisualSetComparison<>("queue names");

        sets.add("third");
        sets.add("first");
        sets.add("second");
        sets.add("first");
        sets.showIterationOrder();
    }
}
