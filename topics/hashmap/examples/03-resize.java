import visual.VisualHashMap;

public class Playground {
    public static void main(String[] args) {
        VisualHashMap<String, Integer> map = new VisualHashMap<>("map");

        // capacity 16 * loadFactor 0.75 = threshold 12.
        // The 13th entry pushes size past the threshold and triggers a resize
        // to 32 buckets, rehashing every existing entry.
        for (int i = 0; i < 13; i++) {
            map.put("key" + i, i);
        }

        System.out.println("size = " + map.size());
    }
}
