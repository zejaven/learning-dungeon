import visual.VisualHashMap;

public class Playground {
    public static void main(String[] args) {
        VisualHashMap<String, Integer> scores = new VisualHashMap<>("scores");

        // "Aa" and "BB" both have hashCode() == 2112 in Java.
        scores.put("Aa", 10);
        scores.put("BB", 20);

        System.out.println("BB -> " + scores.get("BB"));
    }
}
