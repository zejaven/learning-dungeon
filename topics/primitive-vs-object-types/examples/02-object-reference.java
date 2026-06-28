import visual.VisualMemory;

public class Playground {
    static class Order {
        final String id;
        boolean paid;

        Order(String id) {
            this.id = id;
        }
    }

    public static void main(String[] args) {
        VisualMemory memory = new VisualMemory();

        Order order = new Order("A-100");
        memory.newObject("order", "Order", "id=A-100", "paid=false");

        System.out.println(order.id + " paid=" + order.paid);
    }
}
