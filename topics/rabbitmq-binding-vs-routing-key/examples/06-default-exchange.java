import visual.VisualRouter;

public class Playground {
    public static void main(String[] args) {
        // The nameless default exchange ("") is a direct exchange the broker
        // binds every queue to, with a binding key equal to the queue name.
        VisualRouter router = VisualRouter.defaultExchange();

        router.declareQueue("task-queue");
        router.declareQueue("mail-queue");

        // This is what "publishing straight to a queue" really is.
        router.publish("task-queue", "t1");

        // Matching is exact, so a typo silently sends the message nowhere.
        router.publish("Task-Queue", "t2");

        System.out.println("publishing to a queue = default exchange + routing key = queue name");
    }
}
