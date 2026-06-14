import visual.VisualChain;

public class Playground {
    public static void main(String[] args) {
        VisualChain approvals = new VisualChain("approvals");
        approvals.addHandler("TeamLead", 1_000)
                 .addHandler("Manager", 10_000)
                 .addHandler("Director", 100_000);

        // 500_000 is over every handler's limit. The request walks the whole
        // chain and falls off the end UNHANDLED (handle() returns null). This is
        // why a real chain usually ends with a default / catch-all tail handler.
        Integer approvedBy = approvals.handle("datacenter", 500_000);
        System.out.println("Approved with limit: " + approvedBy); // null
    }
}
