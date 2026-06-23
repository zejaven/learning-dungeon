import visual.VisualHashMap;

public class Playground {
    public static void main(String[] args) {
        VisualHashMap<String, String> cache = new VisualHashMap<>("cache");

        cache.put("order-101", "paid");
        cache.put("order-102", "new");

        System.out.println("order-101 -> " + cache.get("order-101"));
    }
}
