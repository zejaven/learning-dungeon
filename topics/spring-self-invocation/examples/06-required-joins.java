import visual.VisualSelfInvocation;

public class Playground {
    public static void main(String[] args) {
        // With the fix in place, propagation finally behaves as declared.
        // The outer @Transactional method opens tx-1; the inner method, reached
        // through the proxy with propagation=REQUIRED, JOINS tx-1 instead of
        // opening a new one. (Had it been REQUIRES_NEW, a tx-2 would open.)
        new VisualSelfInvocation("OrderService")
                .transactional("placeOrder", "REQUIRED")
                .transactional("saveItems", "REQUIRED")
                .externalCall("placeOrder")
                .proxyInvoke("saveItems")        // through proxy -> interceptor runs
                .work("itemRepo.saveAll(items)")
                .ret()
                .ret();
    }
}
