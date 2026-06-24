import visual.VisualTreeSet;

import java.util.List;

public class Playground {
    public static void main(String[] args) {
        VisualTreeSet<Integer> orderTotals = new VisualTreeSet<>("orderTotals");

        orderTotals.add(5);
        orderTotals.add(10);
        orderTotals.add(25);
        orderTotals.add(40);
        orderTotals.add(60);

        List<Integer> mediumOrders = orderTotals.range(10, 50);
        System.out.println("Orders from 10 inclusive to 50 exclusive: " + mediumOrders);
    }
}
