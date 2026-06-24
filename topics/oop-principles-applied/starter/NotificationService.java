// The service that uses a notifier. It should NOT know which concrete notifier
// is used — it just holds a Notifier and delegates to it (composition over
// inheritance). The hidden recipient list shows encapsulation: keep it private.
//
// TODO (missions):
//   1. Add at least two classes that implement Notifier
//      (e.g. EmailNotifier, SmsNotifier) — abstraction + polymorphism.
//   2. Add a subclass that `extends` one of them (e.g. UrgentEmailNotifier) —
//      inheritance ("is-a").
//   3. Give NotificationService a `Notifier` field and delegate to it in
//      notify(...) — composition.
public class NotificationService {

    public void notify(String message) {
        // TODO: delegate to the Notifier this service holds
    }
}
