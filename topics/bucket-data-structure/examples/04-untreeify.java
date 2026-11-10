import visual.VisualBucket;

public class Playground {
    public static void main(String[] args) {
        // Build a treeified bucket (9 entries, capacity 64).
        VisualBucket bucket = new VisualBucket(5, 64);
        for (int i = 0; i < 9; i++) {
            bucket.add("k" + i, "v" + i);
        }

        // Removing entries shrinks the tree. remove() does not count: it turns
        // the tree back into a list once it is too small BY SHAPE (the root's
        // left child has no left child). For this balanced tree that is 6 nodes;
        // a real tree of colliding keys can reach it anywhere from about 6 to 2.
        bucket.remove("k0");
        bucket.remove("k1");
        bucket.remove("k2"); // 6 left -> too small by shape -> untreeify

        System.out.println("size -> " + bucket.size());
    }
}
