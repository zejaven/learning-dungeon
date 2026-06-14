import visual.VisualLinkedList;

public class Playground {
    public static void main(String[] args) {
        VisualLinkedList<String> list = new VisualLinkedList<>("list");
        list.add("a");
        list.add("b");
        list.add("c");
        list.add("d");

        // Inserting in the MIDDLE is two costs added together: an O(n) walk
        // to reach the position, then an O(1) relink. Nothing is shifted or
        // copied — unlike ArrayList, only two pointers change.
        list.add(2, "x");

        System.out.println("get(2) = " + list.get(2));
    }
}
