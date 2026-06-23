import visual.VisualCriticalSection;

public class Playground {
    public static void main(String[] args) {
        VisualCriticalSection counter = new VisualCriticalSection("counter", 10);

        counter.outsideWork("T1");

        counter.enter("T1");
        int current = counter.read("T1");
        counter.write("T1", current + 5);
        counter.exit("T1");

        counter.outsideWork("T1");
        System.out.println("counter = " + counter.value());
    }
}
