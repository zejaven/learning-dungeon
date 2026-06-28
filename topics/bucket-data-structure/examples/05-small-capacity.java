import visual.VisualBucket;

public class Playground {
    public static void main(String[] args) {
        // Capacity 16 is below MIN_TREEIFY_CAPACITY (64). A long chain does NOT
        // treeify here: the real HashMap resizes first, because spreading the
        // entries over more buckets is cheaper than building a tree in a tiny table.
        VisualBucket bucket = new VisualBucket(0, 16);

        for (int i = 0; i < 8; i++) {
            bucket.add("k" + i, "v" + i); // reaches 8 but stays a list
        }

        System.out.println("still a list, size -> " + bucket.size());
    }
}
