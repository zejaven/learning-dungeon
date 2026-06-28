import visual.VisualBucket;

public class Playground {
    public static void main(String[] args) {
        // Build a treeified bucket (8 entries, capacity 64).
        VisualBucket bucket = new VisualBucket(5, 64);
        for (int i = 0; i < 8; i++) {
            bucket.add("k" + i, "v" + i);
        }

        // Removing entries shrinks the tree. At 6 nodes (UNTREEIFY_THRESHOLD)
        // the bucket converts back into a plain linked list.
        bucket.remove("k0");
        bucket.remove("k1"); // count drops to 6 -> untreeify

        System.out.println("size -> " + bucket.size());
    }
}
