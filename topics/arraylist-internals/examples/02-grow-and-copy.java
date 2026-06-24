import visual.VisualArrayList;

public class Playground {
    public static void main(String[] args) {
        // Capacity starts at 4 in this teaching model.
        // The 5th append finds no free slot, so the backing array grows.
        VisualArrayList<String> parcels = new VisualArrayList<>("parcels");

        parcels.add("A-101");
        parcels.add("A-102");
        parcels.add("A-103");
        parcels.add("A-104");
        parcels.add("A-105");

        System.out.println("size = " + parcels.size());
    }
}
