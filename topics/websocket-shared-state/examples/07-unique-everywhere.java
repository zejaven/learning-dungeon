import visual.VisualSharedState;
import visual.VisualSharedState.Source;
import visual.VisualSharedState.Store;

public class Playground {
    public static void main(String[] args) {
        // Option 1: ask nobody. 122 random bits are wide enough.
        VisualSharedState random = VisualSharedState.cluster(
                Store.CONCURRENT_MAP, Source.RANDOM_UUID, 2);
        random.connect("alice");
        random.connect("bob");
        random.join("alice");
        random.join("bob");
        random.report();

        // Option 2: ask the one thing both nodes share.
        VisualSharedState sequence = VisualSharedState.cluster(
                Store.CONCURRENT_MAP, Source.DB_SEQUENCE, 2);
        sequence.connect("alice");
        sequence.connect("bob");
        sequence.join("alice");
        sequence.join("bob");
        sequence.report();

        System.out.println("Generate it wide, or ask a single authority.");
    }
}
