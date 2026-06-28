import visual.VisualNaiveArrayList;

public class Playground {
    public static void main(String[] args) {
        VisualNaiveArrayList<String> parcels = new VisualNaiveArrayList<>("parcels");

        parcels.add("P-1");
        parcels.add("P-2");
        parcels.add("P-3");
        parcels.add("P-4");
        parcels.reportTotalWork();

        System.out.println("copies = " + parcels.totalCopies());
    }
}
