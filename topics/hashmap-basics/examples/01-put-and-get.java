import visual.VisualHashMap;

public class Playground {
    public static void main(String[] args) {
        VisualHashMap<String, String> usersById = new VisualHashMap<>("usersById");

        usersById.put("u-100", "Alice");

        System.out.println("u-100 -> " + usersById.get("u-100"));
    }
}
