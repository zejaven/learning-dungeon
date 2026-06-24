import visual.VisualArrayList;

public class Playground {
    public static void main(String[] args) {
        VisualArrayList<String> parcels = new VisualArrayList<>("parcels");
        parcels.add("A-101");
        parcels.add("A-102");
        parcels.add("A-103");

        // Inserting near the front shifts existing elements to the right.
        parcels.add(1, "VIP");

        System.out.println("size = " + parcels.size());
    }
}
