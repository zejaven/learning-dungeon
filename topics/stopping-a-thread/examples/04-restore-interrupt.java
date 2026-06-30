import visual.VisualThreadStop;

public class Playground {
    public static void main(String[] args) {
        VisualThreadStop demo = new VisualThreadStop("restore-interrupt");

        demo.createWorker("queue-consumer");
        demo.start("queue-consumer");
        demo.block("queue-consumer", "BlockingQueue.take()");

        demo.interrupt("queue-consumer");
        demo.handleInterruptedException("queue-consumer");
        demo.restoreInterruptStatus("queue-consumer");
        demo.exit("queue-consumer");
        demo.join("queue-consumer");
    }
}
