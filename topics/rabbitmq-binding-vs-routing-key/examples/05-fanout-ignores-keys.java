import visual.VisualRouter;

public class Playground {
    public static void main(String[] args) {
        // A fanout exchange reads neither key: the binding alone decides.
        VisualRouter router = new VisualRouter("events", "fanout");

        router.bind("audit");                          // no binding key at all
        router.bind("search-index");
        router.bind("cache", "this.key.is.never.read"); // a binding key here is dead weight

        router.publish("user.updated", "e1");  // every bound queue gets a copy
        router.publish("", "e2");              // even an empty routing key reaches all three

        System.out.println("fanout: the exchange type outranks both keys");
    }
}
