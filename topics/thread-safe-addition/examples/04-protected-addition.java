import visual.VisualCriticalSection;

public class Playground {
    public static void main(String[] args) {
        VisualCriticalSection counter = new VisualCriticalSection("counter", 0);

        counter.enter("T1");
        int oldValue = counter.read("T1");
        counter.write("T1", oldValue + 1);
        counter.exit("T1");

        System.out.println("counter = " + counter.value());
    }
}
