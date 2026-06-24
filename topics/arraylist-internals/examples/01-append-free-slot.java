import visual.VisualArrayList;

public class Playground {
    public static void main(String[] args) {
        // The visual model starts with capacity 4 so the slots are easy to see.
        // While capacity is available, append writes to elementData[size].
        VisualArrayList<String> parcels = new VisualArrayList<>("parcels");

        parcels.add("A-101");
        parcels.add("A-102");
        parcels.add("A-103");

        System.out.println("size = " + parcels.size());
    }
}
