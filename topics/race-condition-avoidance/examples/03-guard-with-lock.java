import visual.VisualCriticalSection;

public class Playground {
    public static void main(String[] args) {
        VisualCriticalSection stock = new VisualCriticalSection("stock", 10);

        stock.enter("Worker-A");
        int current = stock.read("Worker-A");
        stock.write("Worker-A", current - 1);
        stock.exit("Worker-A");

        System.out.println("stock = " + stock.value());
    }
}
