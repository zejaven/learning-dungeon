import visual.VisualChain;

public class Playground {
    public static void main(String[] args) {
        // The pattern's payoff: the sender code (handle(...)) never changes.
        // First the chain is too short and a big request goes unhandled.
        VisualChain approvals = new VisualChain("approvals");
        approvals.addHandler("TeamLead", 1_000)
                 .addHandler("Manager", 10_000);

        approvals.handle("server", 25_000); // unhandled — nobody can approve it

        // Adding a new handler at the tail extends the chain without touching the
        // request-sending code. The same request now finds an approver.
        approvals.addHandler("Director", 100_000);
        Integer approvedBy = approvals.handle("server", 25_000);
        System.out.println("Approved with limit: " + approvedBy); // 100000
    }
}
