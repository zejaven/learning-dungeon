// The abstract base class owns the stable order of the algorithm.
// Your job is to add concrete subclasses that fill in the steps.
public abstract class OrderWorkflow {

    public final void process(String orderId) {
        validateOrder(orderId);
        reserveInventory(orderId);
        chargeCustomer(orderId);
        sendReceipt(orderId);
    }

    protected abstract void validateOrder(String orderId);

    protected abstract void reserveInventory(String orderId);

    protected abstract void chargeCustomer(String orderId);

    protected void sendReceipt(String orderId) {
        System.out.println("Receipt sent for " + orderId);
    }
}
