import visual.VisualHashMap;

public class Playground {
    public static void main(String[] args) {
        VisualHashMap<String, String> parcels = new VisualHashMap<>("parcels");

        parcels.put("parcel-1", "packed");

        System.out.println("parcel-404 -> " + parcels.get("parcel-404"));
    }
}
