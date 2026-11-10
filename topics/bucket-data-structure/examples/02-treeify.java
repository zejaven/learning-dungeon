import visual.VisualBucket;

public class Playground {
    public static void main(String[] args) {
        // Capacity is 64, so once the chain grows past 8 entries the bucket
        // treeifies: the linked list becomes a red-black tree of TreeNode.
        VisualBucket bucket = new VisualBucket(5, 64);

        for (int i = 0; i < 9; i++) {
            bucket.add("k" + i, "v" + i); // the 9th add grows past TREEIFY_THRESHOLD
        }

        // Further inserts now go into the tree and keep it balanced.
        bucket.add("k9", "v9");
        bucket.add("k10", "v10");
    }
}
