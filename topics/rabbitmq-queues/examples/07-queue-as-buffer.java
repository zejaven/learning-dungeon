import visual.VisualBroker;

public class Playground {
    public static void main(String[] args) {
        VisualBroker broker = new VisualBroker();

        broker.declareExchange("jobs", "direct");
        broker.declareQueue("jobs.resize");
        broker.bind("jobs", "jobs.resize", "resize");

        // A burst arrives while NO consumer is attached. Nothing fails: the queue
        // absorbs it. The producer returns immediately and never waits for a worker
        // — that decoupling is the whole point of a queue.
        broker.publish("jobs", "resize", "j1", "photo-1.png");
        broker.publish("jobs", "resize", "j2", "photo-2.png");
        broker.publish("jobs", "resize", "j3", "photo-3.png");
        broker.publish("jobs", "resize", "j4", "photo-4.png");

        System.out.println("backlog of 4, producer already finished");

        // A worker comes online and drains the backlog at its own pace. Queue depth
        // is the metric to watch: growing depth means consumers are too slow.
        broker.subscribe("worker-1", "jobs.resize", 2);
        broker.ack("j1");
        broker.ack("j2");
        broker.ack("j3");
        broker.ack("j4");

        System.out.println("queue drained back to empty");
    }
}
