import visual.VisualProxyFactory;

public class Playground {
    public static void main(String[] args) {
        // The bean implements an interface, but proxyTargetClass=true forces a
        // class-based CGLIB proxy. This is the Spring Boot default since 2.0.
        new VisualProxyFactory("PaymentService")
                .implementsInterface("PaymentApi")
                .method("pay")
                .proxyTargetClass(true)
                .createProxy()
                .invoke("pay");
    }
}
