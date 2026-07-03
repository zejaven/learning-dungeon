import visual.VisualJpaEntityLifecycle;

public class Playground {
    public static void main(String[] args) {
        VisualJpaEntityLifecycle jpa = new VisualJpaEntityLifecycle("orders");
        jpa.seedRow(100, "Order #100");

        VisualJpaEntityLifecycle.Entity order = jpa.find("order", 100);
        jpa.detach(order);

        jpa.change(order, "Order #100 edited outside context");
        jpa.flush();
    }
}
