import visual.VisualProxyFactory;

public class Playground {
    public static void main(String[] args) {
        // The bean implements an interface, so Spring AOP defaults to a
        // JDK dynamic proxy: a sibling that implements the same interface.
        new VisualProxyFactory("PaymentService")
                .implementsInterface("PaymentApi")
                .method("pay")
                .createProxy()
                .invoke("pay");
    }
}
