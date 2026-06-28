import visual.VisualSetComparison;

public class Playground {
    public static void main(String[] args) {
        VisualSetComparison<String> sets = new VisualSetComparison<>("optional labels");

        sets.add("printed");
        sets.add(null);
        sets.showIterationOrder();
    }
}
