import visual.VisualSharedState;

public class Playground {
    public static void main(String[] args) {
        // Two different questions, one table: is it unique among the threads of
        // this JVM, and is it still unique once there are two JVMs?
        VisualSharedState.compareValueSources();

        System.out.println("Thread-safe and globally unique are not the same guarantee.");
    }
}
