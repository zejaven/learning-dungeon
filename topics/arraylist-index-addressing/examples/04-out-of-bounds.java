import visual.VisualArrayIndexing;

public class Playground {
    public static void main(String[] args) {
        VisualArrayIndexing list = new VisualArrayIndexing("small", 4);
        list.store("X");
        list.store("Y");

        // Index 5 is outside [0, size). The bounds check fails first, so no
        // address is ever computed — the JVM throws instead of reading memory.
        try {
            list.get(5);
        } catch (IndexOutOfBoundsException e) {
            System.out.println("blocked: " + e.getMessage());
        }
    }
}
