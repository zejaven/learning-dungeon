import visual.VisualHashMap;

public class Playground {
    public static void main(String[] args) {
        VisualHashMap<String, Integer> table = new VisualHashMap<>("table");

        for (int i = 1; i <= 13; i++) {
            table.put("ticket-" + i, i);
        }

        System.out.println("size -> " + table.size());
    }
}
