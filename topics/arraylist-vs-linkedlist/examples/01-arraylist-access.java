import visual.VisualArrayList;

public class Playground {
    public static void main(String[] args) {
        // VisualArrayList is a teaching model of java.util.ArrayList:
        // a resizable array. Appending writes into the next free slot,
        // and get(i) jumps straight to slot i — both are O(1).
        VisualArrayList<String> list = new VisualArrayList<>("list");

        list.add("a");
        list.add("b");
        list.add("c");

        System.out.println("get(2) = " + list.get(2));
    }
}
