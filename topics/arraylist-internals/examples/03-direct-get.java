import visual.VisualArrayList;

public class Playground {
    public static void main(String[] args) {
        VisualArrayList<String> parcels = new VisualArrayList<>("parcels");
        parcels.add("A-101");
        parcels.add("A-102");
        parcels.add("A-103");

        // get(1) jumps directly to slot 1.
        System.out.println("middle = " + parcels.get(1));
    }
}
