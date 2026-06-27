import visual.VisualSelfInvocation;

public class Playground {
    public static void main(String[] args) {
        // A client calls a @Transactional method through the proxy reference.
        // The interceptor opens a transaction, the work runs inside it, commit.
        new VisualSelfInvocation("OrderService")
                .transactional("saveOrder", "REQUIRED")
                .externalCall("saveOrder")
                .work("repository.save(order)")
                .ret();
    }
}
