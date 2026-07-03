import visual.VisualJpaEntityLifecycle;

public class Playground {
    public static void main(String[] args) {
        VisualJpaEntityLifecycle jpa = new VisualJpaEntityLifecycle("orders");
        jpa.seedRow(401, "Order #401");
        jpa.seedRow(402, "Order #402");

        jpa.find("first", 401);
        jpa.find("second", 402);

        jpa.clear();
        jpa.close();
    }
}
