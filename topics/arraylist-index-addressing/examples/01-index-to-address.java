import visual.VisualArrayIndexing;

public class Playground {
    public static void main(String[] args) {
        // The backing array of an ArrayList is one contiguous block of slots,
        // each holding a fixed-width reference. get(i) does not search — it
        // turns the index into an address by arithmetic.
        VisualArrayIndexing list = new VisualArrayIndexing("names", 4);
        list.store("Alice");
        list.store("Bob");
        list.store("Carol");

        // get(2): bounds check -> address = base + header + 2 * scale -> follow the reference.
        System.out.println("index 2 -> " + list.get(2));
    }
}
