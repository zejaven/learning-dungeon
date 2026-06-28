import visual.VisualArrayIndexing;

public class Playground {
    public static void main(String[] args) {
        // Each slot stores a reference (a pointer), not the object itself.
        // The objects live elsewhere on the heap; the array only holds the
        // addresses that point to them.
        VisualArrayIndexing list = new VisualArrayIndexing("orders", 4);
        list.store("Order#1");
        list.store("Order#2");

        System.out.println("slot 0 -> " + list.get(0));
        System.out.println("slot 1 -> " + list.get(1));
    }
}
