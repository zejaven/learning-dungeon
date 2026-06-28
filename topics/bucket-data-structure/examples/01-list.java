import visual.VisualBucket;

public class Playground {
    public static void main(String[] args) {
        // A bucket starts life as a singly-linked list of Node objects.
        // Every key here is assumed to collide into the same bucket (index 5).
        VisualBucket bucket = new VisualBucket(5, 64);

        bucket.add("k0", "v0");
        bucket.add("k1", "v1");
        bucket.add("k2", "v2");

        // get() walks the chain node by node — O(n) in the bucket length.
        System.out.println("k2 -> " + bucket.get("k2"));
        System.out.println("missing -> " + bucket.get("k9"));
    }
}
