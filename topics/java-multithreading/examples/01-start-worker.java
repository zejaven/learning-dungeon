import visual.VisualThread;

public class Playground {
    public static void main(String[] args) {
        VisualThread demo = new VisualThread("order-kitchen");

        Runnable cookSoup = demo.runnable("cookSoup", () ->
                System.out.println("Soup is cooked on a worker thread"));

        Thread kitchenWorker = demo.thread("kitchen-worker", cookSoup);
        demo.start(kitchenWorker);
    }
}
