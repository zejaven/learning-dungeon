import visual.VisualCriticalSection;

public class Playground {
    public static void main(String[] args) {
        VisualCriticalSection balance = new VisualCriticalSection("balance", 100);

        balance.outsideWork("Cashier");

        balance.enter("Cashier");
        int current = balance.read("Cashier");
        balance.write("Cashier", current + 20);
        balance.exit("Cashier");

        balance.outsideWork("Cashier");

        System.out.println("balance = " + balance.value());
    }
}
