import visual.VisualArrayList;

public class Playground {
    public static void main(String[] args) {
        VisualArrayList<String> parcels = new VisualArrayList<>("parcels");
        parcels.add("A-101");
        parcels.add("A-102");
        parcels.add("A-103");
        parcels.add("A-104");

        // Removing from the middle shifts the tail left and clears the last slot.
        String removed = parcels.remove(1);

        System.out.println("removed = " + removed);
    }
}
