import visual.VisualCriticalSection;

public class Playground {
    public static void main(String[] args) {
        VisualCriticalSection counter = new VisualCriticalSection("counter", 0);

        counter.enter("T1");
        int t1 = counter.read("T1");

        // T2 cannot enter until T1 releases the same lock.
        counter.enter("T2");

        counter.write("T1", t1 + 1);
        counter.exit("T1");

        int t2 = counter.read("T2");
        counter.write("T2", t2 + 1);
        counter.exit("T2");

        System.out.println("counter = " + counter.value());
    }
}
