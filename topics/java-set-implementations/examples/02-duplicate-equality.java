import visual.VisualSetComparison;

public class Playground {
    public static void main(String[] args) {
        VisualSetComparison<String> sets = new VisualSetComparison<>("invoices");

        sets.add("invoice-42");
        sets.add("invoice-42");
        sets.showIterationOrder();
    }
}
