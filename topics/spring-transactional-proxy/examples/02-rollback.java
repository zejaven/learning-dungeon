import visual.VisualTxProxy;

public class Playground {
    public static void main(String[] args) {
        VisualTxProxy app = new VisualTxProxy("OrderService");

        // The body stages a row, then throws an unchecked exception. The
        // exception propagates out through the proxy, which rolls the
        // transaction back, so the staged row never reaches the database.
        app.proxyCall("placeOrder", true)
                .persist("order-1", "NEW")
                .throwRuntime("DataAccessException");

        System.out.println("Runtime exception rolled the transaction back.");
    }
}
