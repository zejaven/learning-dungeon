import visual.VisualCriticalSection;

public class Playground {
    public static void main(String[] args) {
        VisualCriticalSection counter = new VisualCriticalSection("counter", 0);

        int oldValue = counter.unsafeRead("T1");
        int newValue = oldValue + 1;
        counter.unsafeWrite("T1", newValue);

        System.out.println("counter = " + counter.value());
    }
}
