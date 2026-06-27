import visual.VisualSelfInvocation;

public class Playground {
    public static void main(String[] args) {
        // The classic bug: a plain (non-transactional) method calls a
        // @Transactional method of the SAME bean with this.saveOrder().
        // The internal call skips the proxy, so @Transactional is ignored and
        // the work runs with no transaction at all.
        new VisualSelfInvocation("OrderService")
                .method("placeOrder")
                .transactional("saveOrder", "REQUIRED")
                .externalCall("placeOrder")
                .work("validate(order)")
                .selfInvoke("saveOrder")        // this.saveOrder() -> bypasses proxy
                .work("repository.save(order)")  // runs with NO transaction
                .ret()
                .ret();
    }
}
