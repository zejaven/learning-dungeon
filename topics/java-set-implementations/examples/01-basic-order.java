import visual.VisualSetComparison;

public class Playground {
    public static void main(String[] args) {
        VisualSetComparison<String> sets = new VisualSetComparison<>("delivery stops");

        sets.add("traffic");
        sets.add("kitchen");
        sets.add("post");
        sets.showIterationOrder();
    }
}
