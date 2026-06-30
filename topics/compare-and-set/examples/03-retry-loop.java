import visual.VisualCompareAndSet;

public class Playground {
    public static void main(String[] args) {
        VisualCompareAndSet counter = new VisualCompareAndSet("counter", 0);

        int expected = counter.read("T1");
        counter.compareAndSet("T2", 0, 1);

        while (!counter.compareAndSet("T1", expected, expected + 1)) {
            expected = counter.retryRead("T1");
        }
    }
}
