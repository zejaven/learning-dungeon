import visual.VisualChain;

public class Playground {
    public static void main(String[] args) {
        VisualChain approvals = new VisualChain("approvals");
        approvals.addHandler("TeamLead", 1_000)
                 .addHandler("Manager", 10_000)
                 .addHandler("Director", 100_000);

        // 25_000 is too big for TeamLead and for Manager, so the request is
        // escalated step by step until Director — the first one able to approve
        // it — handles it. The sender never had to know who that would be.
        Integer approvedBy = approvals.handle("server", 25_000);
        System.out.println("Approved with limit: " + approvedBy);
    }
}
