import visual.VisualHashMap;

public class Playground {
    public static void main(String[] args) {
        VisualHashMap<String, Integer> map = new VisualHashMap<>("orders");

        // Default threshold is 12. The 13th insert triggers resize.
        for (int i = 0; i < 13; i++) {
            map.put("order-" + i, i);
        }

        System.out.println("order-12 -> " + map.get("order-12"));
    }
}
