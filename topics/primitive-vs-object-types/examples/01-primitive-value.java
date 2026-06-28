import visual.VisualMemory;

public class Playground {
    public static void main(String[] args) {
        VisualMemory memory = new VisualMemory();

        int seats = 4;
        memory.primitive("seats", "int", String.valueOf(seats));

        int spareSeats = seats;
        memory.copyPrimitive("spareSeats", "int", "seats");

        spareSeats = 6;
        memory.setPrimitive("spareSeats", String.valueOf(spareSeats));

        System.out.println("seats=" + seats + ", spareSeats=" + spareSeats);
    }
}
