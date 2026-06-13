import visual.VisualHashMap;

public class Playground {
    public static void main(String[] args) {
        // VisualHashMap is a teaching model of java.util.HashMap.
        VisualHashMap<String, Integer> map = new VisualHashMap<>("map");

        map.put("Alice", 10);
        map.put("Bob", 20);

        System.out.println("Alice -> " + map.get("Alice"));
    }
}
