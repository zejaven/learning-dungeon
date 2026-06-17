// The context. It should NOT know which concrete payment is used — it just holds
// a PaymentStrategy and delegates to it.
//
// TODO (missions):
//   1. Create at least two classes that implement PaymentStrategy
//      (e.g. CardPayment, CashPayment).
//   2. Give Checkout a `PaymentStrategy` field and use it in checkout(...).
public class Checkout {

    public void checkout(int amount) {
        // TODO: delegate to the chosen PaymentStrategy
    }
}
