import visual.VisualCompareAndSet;

public class Playground {
    public static void main(String[] args) {
        VisualCompareAndSet state = new VisualCompareAndSet("state", 1);

        int expected = state.read("T1");
        state.compareAndSet("T2", 1, 2);
        state.compareAndSet("T2", 2, 1);
        state.compareAndSet("T1", expected, 3);
    }
}
