import visual.VisualArrayList;

public class Playground {
    public static void main(String[] args) {
        VisualArrayList<String> list = new VisualArrayList<>("list");
        list.add("a");
        list.add("b");
        list.add("c");

        // Inserting at the FRONT is the worst case for an ArrayList:
        // every existing element must be shifted one slot to the right
        // before the new value can be written. That makes it O(n).
        list.add(0, "x");

        System.out.println("get(0) = " + list.get(0));
    }
}
