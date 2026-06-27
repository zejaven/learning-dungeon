import visual.VisualSelfInvocation;

public class Playground {
    public static void main(String[] args) {
        // Fix #1: call the inner method through an injected self/proxy reference
        // (self.saveOrder()) instead of this.saveOrder(). The call re-enters the
        // proxy, the interceptor runs, and @Transactional takes effect.
        new VisualSelfInvocation("OrderService")
                .method("placeOrder")
                .transactional("saveOrder", "REQUIRES_NEW")
                .externalCall("placeOrder")
                .work("validate(order)")
                .proxyInvoke("saveOrder")        // self.saveOrder() -> back through proxy
                .work("repository.save(order)")  // runs inside its own transaction
                .ret()
                .ret();
    }
}
