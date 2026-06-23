import visual.VisualHashMap;

public class Playground {
    public static void main(String[] args) {
        VisualHashMap<String, Integer> map = new VisualHashMap<>("collisions");

        // "Aa" and "BB" have the same hashCode() in Java.
        // They land in one bucket, so lookup must compare keys in that chain.
        map.put("Aa", 1);
        map.put("BB", 2);

        System.out.println("BB -> " + map.get("BB"));
    }
}
