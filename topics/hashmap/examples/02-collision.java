import visual.VisualHashMap;

public class Playground {
    public static void main(String[] args) {
        VisualHashMap<String, Integer> map = new VisualHashMap<>("map");

        // "Aa" and "BB" both have hashCode() == 2112 in Java,
        // so they collide into the SAME bucket and form a chain.
        map.put("Aa", 1);
        map.put("BB", 2);

        System.out.println("Aa -> " + map.get("Aa"));
        System.out.println("BB -> " + map.get("BB"));
    }
}
