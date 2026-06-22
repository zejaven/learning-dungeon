import visual.VisualTxProxy;

public class Playground {
    public static void main(String[] args) {
        // Spring wraps the bean in a proxy. An external caller (a controller)
        // invokes placeOrder() through that proxy reference.
        VisualTxProxy app = new VisualTxProxy("OrderService");

        // The method is @Transactional, so the proxy opens a transaction, lets
        // the body run, and commits when the method returns normally.
        app.proxyCall("placeOrder", true)
                .persist("order-1", "NEW")
                .returnNormally();

        System.out.println("Order committed through the transactional proxy.");
    }
}
