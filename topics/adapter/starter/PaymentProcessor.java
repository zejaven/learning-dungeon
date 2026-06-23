// Target interface expected by the application.
// TODO (missions):
//   1. Add a LegacyPaymentGateway class with its own incompatible method.
//   2. Add LegacyPaymentAdapter that implements PaymentProcessor.
//   3. Let the adapter hold a LegacyPaymentGateway field and translate calls.
//   4. Let CheckoutService hold a PaymentProcessor field, not the legacy class.
public interface PaymentProcessor {
    void processPayment(int cents);
}
