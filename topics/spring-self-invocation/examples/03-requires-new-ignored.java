import visual.VisualSelfInvocation;

public class Playground {
    public static void main(String[] args) {
        // A subtler bug: the outer method IS @Transactional, and it self-invokes
        // an audit method declared @Transactional(REQUIRES_NEW). You expect a
        // separate, independent transaction. Because the call skips the proxy,
        // REQUIRES_NEW is ignored and the audit just runs inside the outer
        // transaction -- it is NOT isolated and will roll back with it.
        new VisualSelfInvocation("OrderService")
                .transactional("placeOrder", "REQUIRED")
                .transactional("writeAudit", "REQUIRES_NEW")
                .externalCall("placeOrder")
                .work("repository.save(order)")
                .selfInvoke("writeAudit")        // expected a NEW tx, but proxy is skipped
                .work("auditRepo.save(entry)")   // runs inside the OUTER tx
                .ret()
                .ret();
    }
}
