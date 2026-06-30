import visual.VisualCompareAndSet;

public class Playground {
    public static void main(String[] args) {
        VisualCompareAndSet stock = new VisualCompareAndSet("stock", 5);

        int staleExpected = stock.read("T1");
        stock.compareAndSet("T2", 5, 4);
        stock.compareAndSet("T1", staleExpected, staleExpected - 1);
    }
}
