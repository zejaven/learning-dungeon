import visual.VisualSpringTransaction;

public class Playground {
    public static void main(String[] args) {
        VisualSpringTransaction app = new VisualSpringTransaction("orders");

        // The external call is still inside the @Transactional method. Because
        // it throws a runtime exception before the proxy commits, the earlier
        // persist is rolled back.
        app.transactional("createOrder")
                .persist("order-3", "NEW")
                .externalCallThrowsRuntime("paymentClient", "PaymentGatewayTimeoutException")
                .complete();

        System.out.println("The external runtime failure rolls back the order.");
    }
}
