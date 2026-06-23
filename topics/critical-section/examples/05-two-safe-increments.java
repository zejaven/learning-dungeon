import visual.VisualCriticalSection;

public class Playground {
    public static void main(String[] args) {
        VisualCriticalSection counter = new VisualCriticalSection("counter", 0);

        increment(counter, "T1");
        increment(counter, "T2");

        System.out.println("counter = " + counter.value());
    }

    private static void increment(VisualCriticalSection counter, String thread) {
        counter.enter(thread);
        int value = counter.read(thread);
        counter.write(thread, value + 1);
        counter.exit(thread);
    }
}
