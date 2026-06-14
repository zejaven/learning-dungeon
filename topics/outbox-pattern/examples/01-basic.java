import visual.VisualOutbox;

public class Playground {
    public static void main(String[] args) {
        // VisualOutbox is a teaching model of the transactional outbox pattern.
        // A service must both change its database AND publish an event. The
        // outbox table lives in the SAME database as the business data.
        VisualOutbox outbox = new VisualOutbox("orders");

        // One local transaction writes the order to the business table AND
        // stages an event row in the outbox table. They commit together, so the
        // event can never be staged for an order that was not saved.
        outbox.placeOrder("o1", 100);

        System.out.println("Order saved and event staged in one transaction.");
    }
}
