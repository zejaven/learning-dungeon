import visual.VisualBroker;

public class Playground {
    public static void main(String[] args) {
        VisualBroker broker = new VisualBroker();

        // A topic exchange matches dot-separated routing keys against patterns:
        //   *  exactly one word
        //   #  zero or more words
        broker.declareExchange("logs", "topic");
        broker.declareQueue("errors");
        broker.declareQueue("payments");
        broker.declareQueue("archive");
        broker.bind("logs", "errors", "*.error");
        broker.bind("logs", "payments", "payment.#");
        broker.bind("logs", "archive", "#");

        // "payment" + "error" -> matches all three patterns.
        broker.publish("logs", "payment.error", "l1", "card declined");

        // "*.error" matches, "payment.#" does not -> errors + archive.
        broker.publish("logs", "auth.error", "l2", "bad password");

        // Three words: "*" cannot span two, so only "payment.#" and "#" match.
        broker.publish("logs", "payment.retry.ok", "l3", "retry succeeded");

        System.out.println("one publish can feed several queues, or none");
    }
}
