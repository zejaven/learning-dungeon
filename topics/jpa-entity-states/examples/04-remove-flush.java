import visual.VisualJpaEntityLifecycle;

public class Playground {
    public static void main(String[] args) {
        VisualJpaEntityLifecycle jpa = new VisualJpaEntityLifecycle("orders");
        jpa.seedRow(300, "Order #300");

        VisualJpaEntityLifecycle.Entity order = jpa.find("order", 300);
        jpa.remove(order);
        jpa.flush();
    }
}
