import visual.VisualSetComparison;

public class Playground {
    public static void main(String[] args) {
        VisualSetComparison<Integer> sets = new VisualSetComparison<>("ticket numbers");

        sets.add(20);
        sets.add(10);
        sets.add(30);
        sets.contains(20);
        sets.remove(10);
        sets.showIterationOrder();
    }
}
