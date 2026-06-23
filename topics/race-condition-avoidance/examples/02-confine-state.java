import visual.VisualCriticalSection;

public class Playground {
    public static void main(String[] args) {
        VisualCriticalSection shift = new VisualCriticalSection("shift total", 0);

        int clerkALocal = 1;
        int clerkBLocal = 1;

        shift.outsideWork("Clerk-A");
        shift.outsideWork("Clerk-B");

        int finalTotal = clerkALocal + clerkBLocal;
        System.out.println("combined after local work = " + finalTotal);
    }
}
