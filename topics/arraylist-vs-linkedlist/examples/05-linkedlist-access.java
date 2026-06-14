import visual.VisualLinkedList;

public class Playground {
    public static void main(String[] args) {
        VisualLinkedList<Integer> list = new VisualLinkedList<>("list");
        for (int i = 0; i < 6; i++) {
            list.add(i);
        }

        // There is no array to index into: get(i) must WALK the chain.
        // LinkedList walks from whichever end is closer, so get(4) starts
        // at the tail. Each step is a "hop" — that is the O(n) cost.
        System.out.println("get(4) = " + list.get(4));
    }
}
