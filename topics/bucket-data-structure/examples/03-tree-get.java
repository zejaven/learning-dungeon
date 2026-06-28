import visual.VisualBucket;

public class Playground {
    public static void main(String[] args) {
        // Fill the bucket past the threshold so it is a red-black tree.
        VisualBucket bucket = new VisualBucket(5, 64);
        for (int i = 0; i < 10; i++) {
            bucket.add("k" + i, "v" + i);
        }

        // A tree bucket searches by hash in O(log n) comparisons, not O(n).
        System.out.println("k7 -> " + bucket.get("k7"));
        System.out.println("k0 -> " + bucket.get("k0"));
        System.out.println("missing -> " + bucket.get("zzz"));
    }
}
