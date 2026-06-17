// The Strategy interface: a single operation every concrete strategy implements.
// (Given as a starting point — your job is to add implementations and a context.)
public interface PaymentStrategy {
    void pay(int amount);
}
