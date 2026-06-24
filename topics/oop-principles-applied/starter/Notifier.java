// The abstraction: WHAT a notifier does, not HOW. Every concrete notifier
// implements this single operation.
// (Given as a starting point — your job is to add implementations, a subclass,
//  and a service that composes this interface.)
public interface Notifier {
    void send(String message);
}
