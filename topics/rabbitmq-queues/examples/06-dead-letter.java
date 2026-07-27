import visual.VisualBroker;

public class Playground {
    public static void main(String[] args) {
        VisualBroker broker = new VisualBroker();

        // Declare the dead-letter queue first, then point the working queue at it.
        broker.declareExchange("payments", "direct");
        broker.declareQueue("payments.dlq");
        broker.declareQueue("payments.charge", "payments.dlq");
        broker.bind("payments", "payments.charge", "charge");
        broker.subscribe("worker-1", "payments.charge", 1);

        broker.publish("payments", "charge", "p1", "charge card 100");

        // The handler threw (malformed payload). nack(requeue = true) puts the
        // message straight back at the head — and it is redelivered immediately.
        // A message that always fails would loop like this forever: a poison message.
        broker.nack("p1", true);

        // So the second time, give up on it: nack(requeue = false) dead-letters it
        // into payments.dlq, where a human (or a retry job) can look at it.
        broker.nack("p1", false);

        // A queue WITHOUT a dead-letter target behaves very differently: the same
        // rejection silently destroys the message.
        broker.declareQueue("payments.receipt");
        broker.bind("payments", "payments.receipt", "receipt");
        broker.subscribe("worker-2", "payments.receipt", 1);
        broker.publish("payments", "receipt", "p2", "send receipt");
        broker.nack("p2", false);

        System.out.println("no dead-letter queue = silent data loss");
    }
}
