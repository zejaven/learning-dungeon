import visual.VisualThread;

public class Playground {
    public static void main(String[] args) {
        VisualThread demo = new VisualThread("lambda-task");

        Runnable task = demo.runnable("auditOrder", Playground::auditOrder);
        Thread worker = demo.thread("audit-worker", task);

        demo.start(worker);
    }

    private static void auditOrder() {
        System.out.println("Auditing one order with a method reference");
    }
}
