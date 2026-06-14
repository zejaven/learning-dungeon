import visual.VisualLinkedList;

public class Playground {
    public static void main(String[] args) {
        // VisualLinkedList is a teaching model of java.util.LinkedList:
        // a doubly-linked chain with a head and a tail. Adding at either
        // end is O(1) — just relink a couple of pointers, no walking.
        VisualLinkedList<String> list = new VisualLinkedList<>("list");

        list.addLast("b");
        list.addLast("c");
        list.addFirst("a"); // cheap at the head too

        System.out.println("size = " + list.size());
    }
}
