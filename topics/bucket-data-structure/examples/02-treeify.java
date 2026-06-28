import visual.VisualBucket;

public class Playground {
    public static void main(String[] args) {
        // Capacity is 64, so once the chain reaches 8 entries the bucket
        // treeifies: the linked list becomes a red-black tree of TreeNode.
        VisualBucket bucket = new VisualBucket(5, 64);

        for (int i = 0; i < 8; i++) {
            bucket.add("k" + i, "v" + i); // the 8th add crosses TREEIFY_THRESHOLD
        }

        // Further inserts now go into the tree and keep it balanced.
        bucket.add("k8", "v8");
        bucket.add("k9", "v9");
    }
}
