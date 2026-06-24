// The client should depend on the abstract workflow, not on one concrete
// implementation.
//
// TODO (missions):
//   1. Create OnlineOrderWorkflow extends OrderWorkflow.
//   2. Create StorePickupWorkflow extends OrderWorkflow.
//   3. Give FulfillmentDesk an OrderWorkflow field and delegate to process(...).
public class FulfillmentDesk {

    public void handle(String orderId) {
        // TODO: call the selected OrderWorkflow
    }
}
