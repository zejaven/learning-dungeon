import visual.VisualArrayList;

public class Playground {
    public static void main(String[] args) {
        // The backing array starts with capacity 4 (the JDK uses 10).
        // The 5th add finds the array full, so ArrayList allocates a bigger
        // array (x1.5) and copies every existing element across.
        VisualArrayList<Integer> list = new VisualArrayList<>("list");

        for (int i = 0; i < 5; i++) {
            list.add(i);
        }

        System.out.println("size = " + list.size());
    }
}
