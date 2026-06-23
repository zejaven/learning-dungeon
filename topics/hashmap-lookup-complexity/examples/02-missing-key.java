import visual.VisualHashMap;

public class Playground {
    public static void main(String[] args) {
        VisualHashMap<String, Integer> scores = new VisualHashMap<>("scores");

        scores.put("Alice", 95);
        scores.put("Bob", 81);

        System.out.println("Charlie -> " + scores.get("Charlie"));
    }
}
