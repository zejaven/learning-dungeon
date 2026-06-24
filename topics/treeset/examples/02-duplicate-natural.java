import visual.VisualTreeSet;

public class Playground {
    public static void main(String[] args) {
        VisualTreeSet<String> names = new VisualTreeSet<>("names");

        names.add("Alice");
        names.add("Bob");
        boolean addedAgain = names.add("Alice");

        System.out.println("Second Alice added? " + addedAgain);
        System.out.println("Names: " + names.values());
    }
}
