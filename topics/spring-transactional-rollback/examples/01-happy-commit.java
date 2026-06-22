import visual.VisualSpringTransaction;

public class Playground {
    public static void main(String[] args) {
        VisualSpringTransaction app = new VisualSpringTransaction("orders");

        // No exception leaves the @Transactional method, so the interceptor
        // commits the staged row.
        app.transactional("createOrder")
                .persist("order-1", "NEW")
                .complete();

        System.out.println("The order is committed.");
    }
}
