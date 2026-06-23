import visual.VisualCriticalSection;

public class Playground {
    public static void main(String[] args) {
        VisualCriticalSection orders = new VisualCriticalSection("confirmed orders", 0);

        int clerkA = orders.unsafeRead("Clerk-A");
        int clerkB = orders.unsafeRead("Clerk-B");

        orders.unsafeWrite("Clerk-A", clerkA + 1);
        orders.unsafeWrite("Clerk-B", clerkB + 1);

        System.out.println("expected 2, actual " + orders.value());
    }
}
