import visual.VisualThread;

public class Playground {
    public static void main(String[] args) {
        VisualThread demo = new VisualThread("delivery-lifecycle");

        Runnable deliverParcel = demo.runnable("deliverParcel", () ->
                System.out.println("Parcel delivered"));

        Thread courier = demo.thread("courier-thread", deliverParcel);
        demo.start(courier);
    }
}
