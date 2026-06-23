import visual.VisualCriticalSection;

public class Playground {
    public static void main(String[] args) {
        VisualCriticalSection counter = new VisualCriticalSection("counter", 0);

        int t1 = counter.unsafeRead("T1");
        int t2 = counter.unsafeRead("T2");

        counter.unsafeWrite("T1", t1 + 1);
        counter.unsafeWrite("T2", t2 + 1);

        System.out.println("counter = " + counter.value());
    }
}
