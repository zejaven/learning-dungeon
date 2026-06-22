import visual.VisualSpringTransaction;

public class Playground {
    public static void main(String[] args) {
        VisualSpringTransaction app = new VisualSpringTransaction("orders");

        // RuntimeException is unchecked. By default Spring rolls the
        // transaction back when it propagates out of the method.
        app.transactional("createOrder")
                .persist("order-2", "NEW")
                .throwRuntime("IllegalStateException")
                .complete();

        System.out.println("The staged order is rolled back.");
    }
}
