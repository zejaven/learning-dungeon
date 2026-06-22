import visual.VisualHashMap;

public class Playground {
    public static void main(String[] args) {
        VisualHashMap<String, String> orderStatus = new VisualHashMap<>("orderStatus");

        orderStatus.put("order-7", "queued");
        orderStatus.put("order-7", "packed");

        System.out.println("order-7 -> " + orderStatus.get("order-7"));
    }
}
