import visual.VisualJpaEntityLifecycle;

public class Playground {
    public static void main(String[] args) {
        VisualJpaEntityLifecycle jpa = new VisualJpaEntityLifecycle("orders");

        VisualJpaEntityLifecycle.Entity order =
                jpa.newEntity("order", "Order draft");

        jpa.persist(order);
        jpa.flush();
    }
}
