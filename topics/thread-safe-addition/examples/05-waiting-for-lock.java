import visual.VisualCriticalSection;

public class Playground {
    public static void main(String[] args) {
        VisualCriticalSection counter = new VisualCriticalSection("counter", 0);

        counter.enter("T1");
        int t1Value = counter.read("T1");

        counter.enter("T2");

        counter.write("T1", t1Value + 1);
        counter.exit("T1");

        int t2Value = counter.read("T2");
        counter.write("T2", t2Value + 1);
        counter.exit("T2");

        System.out.println("counter = " + counter.value());
    }
}
