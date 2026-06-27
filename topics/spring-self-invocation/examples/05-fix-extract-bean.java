import visual.VisualSelfInvocation;

public class Playground {
    public static void main(String[] args) {
        // Fix #2: move the @Transactional method to a SEPARATE bean and inject it.
        // A call from OrderService to AuditService.writeAudit() crosses a real
        // proxy boundary, so the interceptor runs and the transaction is created.
        VisualSelfInvocation audit = new VisualSelfInvocation("AuditService");
        audit.transactional("writeAudit", "REQUIRES_NEW")
                .externalCall("writeAudit")      // OrderService -> AuditService proxy
                .work("auditRepo.save(entry)")
                .ret();
    }
}
