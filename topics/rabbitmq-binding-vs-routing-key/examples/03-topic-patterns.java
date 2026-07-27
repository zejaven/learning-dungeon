import visual.VisualRouter;

public class Playground {
    public static void main(String[] args) {
        // A topic exchange reads the binding key as a pattern over dot-separated words:
        //   *  exactly one word
        //   #  zero or more words
        VisualRouter router = new VisualRouter("logs", "topic");

        router.bind("errors", "*.error");
        router.bind("payments", "payment.#");
        router.bind("archive", "#");

        // The routing key stays a plain, concrete string in every case.
        router.publish("payment.error", "l1");     // matches all three patterns
        router.publish("auth.error", "l2");        // errors + archive
        router.publish("payment.retry.ok", "l3");  // '*' cannot span two words

        System.out.println("the pattern is on the binding side, the concrete key on the message");
    }
}
