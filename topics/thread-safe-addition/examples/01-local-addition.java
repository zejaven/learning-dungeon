import visual.VisualCriticalSection;

public class Playground {
    public static void main(String[] args) {
        VisualCriticalSection model = new VisualCriticalSection("local subtotal", 0);

        int subtotal = 40;
        subtotal = subtotal + 2;

        model.outsideWork("T1");
        System.out.println("local subtotal = " + subtotal);
    }
}
