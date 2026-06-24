import visual.VisualTreeSet;

public class Playground {
    public static void main(String[] args) {
        VisualTreeSet<Integer> ages = new VisualTreeSet<>("ages");

        ages.add(18);
        ages.add(25);
        ages.add(30);
        ages.add(40);

        System.out.println("ceiling(26) = " + ages.ceiling(26));
        System.out.println("lower(30) = " + ages.lower(30));
        System.out.println("higher(30) = " + ages.higher(30));
    }
}
