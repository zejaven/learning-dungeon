import visual.VisualSaleRegistration;

public class Playground {
    public static void main(String[] args) {
        VisualSaleRegistration pos = new VisualSaleRegistration("pos-7");

        // The link is down for a while. The till must keep selling, so sales go
        // into the durable local queue instead of failing at the counter.
        pos.goOffline();
        pos.recordSale("sale-1", "coffee", 250);
        pos.recordSale("sale-2", "bagel", 180);
        pos.recordSale("sale-3", "tea", 120);

        // Connectivity is back: the queue is drained, each sale keeping the key
        // it was born with, so a re-sync can never register anything twice.
        pos.reconnect();

        System.out.println("Store-and-forward: sell offline, sync later, still exactly one row each.");
    }
}
