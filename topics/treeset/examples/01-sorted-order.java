import visual.VisualTreeSet;

public class Playground {
    public static void main(String[] args) {
        VisualTreeSet<Integer> scores = new VisualTreeSet<>("scores");

        scores.add(42);
        scores.add(7);
        scores.add(19);

        System.out.println("Sorted scores: " + scores.values());
    }
}
