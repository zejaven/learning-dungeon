import visual.VisualNaiveArrayList;

public class Playground {
    public static void main(String[] args) {
        VisualNaiveArrayList<String> orders = new VisualNaiveArrayList<>("orders");

        orders.add("A-1");
        orders.reportTotalWork();

        System.out.println("size = " + orders.size());
        System.out.println("copies = " + orders.totalCopies());
    }
}
