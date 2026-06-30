import visual.VisualVolatile;

public class Playground {
    public static void main(String[] args) {
        VisualVolatile counter = new VisualVolatile("counter", true);

        int t1 = counter.readCounter("T1");
        int t2 = counter.readCounter("T2");

        counter.writeCounter("T1", t1 + 1);
        counter.writeCounter("T2", t2 + 1);

        System.out.println("expected 2, actual " + counter.counter());
    }
}
