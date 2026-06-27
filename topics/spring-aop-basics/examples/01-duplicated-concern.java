import visual.VisualAopProxy;

public class Playground {
    public static void main(String[] args) {
        VisualAopProxy app = new VisualAopProxy("OrderService");

        // Before AOP, the same logging call often appears in every method.
        app.duplicatedConcern("logging()", "placeOrder", "cancelOrder", "refundOrder");
    }
}
