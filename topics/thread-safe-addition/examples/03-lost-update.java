import visual.VisualCriticalSection;

public class Playground {
    public static void main(String[] args) {
        VisualCriticalSection counter = new VisualCriticalSection("counter", 0);

        int t1Value = counter.unsafeRead("T1");
        int t2Value = counter.unsafeRead("T2");

        counter.unsafeWrite("T1", t1Value + 1);
        counter.unsafeWrite("T2", t2Value + 1);

        System.out.println("expected 2, actual " + counter.value());
    }
}
