import visual.VisualJpaEntityLifecycle;

public class Playground {
    public static void main(String[] args) {
        VisualJpaEntityLifecycle jpa = new VisualJpaEntityLifecycle("orders");
        jpa.seedRow(200, "Order #200");

        VisualJpaEntityLifecycle.Entity detached = jpa.find("order", 200);
        jpa.detach(detached);
        jpa.change(detached, "Order #200 corrected");

        VisualJpaEntityLifecycle.Entity managedCopy =
                jpa.merge(detached, "managedCopy");
        jpa.change(managedCopy, "Order #200 ready to ship");
        jpa.flush();
    }
}
