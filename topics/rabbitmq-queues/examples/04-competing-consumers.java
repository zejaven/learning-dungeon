import visual.VisualBroker;

public class Playground {
    public static void main(String[] args) {
        VisualBroker broker = new VisualBroker();

        broker.declareExchange("tasks", "direct");
        broker.declareQueue("tasks.run");
        broker.bind("tasks", "tasks.run", "run");

        // Two workers on ONE queue are "competing consumers": each message goes
        // to exactly one of them. This is how a queue scales out horizontally.
        broker.subscribe("worker-1", "tasks.run", 1);
        broker.subscribe("worker-2", "tasks.run", 1);

        broker.publish("tasks", "run", "t1", "resize photo");   // -> worker-1
        broker.publish("tasks", "run", "t2", "resize photo");   // -> worker-2

        // Both workers are now at prefetch 1, so t3 stays in the queue instead of
        // piling up in a worker's socket buffer. That is fair dispatch.
        broker.publish("tasks", "run", "t3", "resize photo");

        // Acking frees a slot, and the broker immediately pushes the next message.
        broker.ack("t1");
        broker.ack("t2");
        broker.ack("t3");

        System.out.println("prefetch is what keeps a slow worker from hoarding work");
    }
}
