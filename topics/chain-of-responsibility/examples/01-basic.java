import visual.VisualChain;

public class Playground {
    public static void main(String[] args) {
        // VisualChain is a teaching model of the Chain of Responsibility pattern.
        // Each handler approves requests up to its own limit, otherwise it passes
        // the request on to the next handler in the chain.
        VisualChain approvals = new VisualChain("approvals");
        approvals.addHandler("TeamLead", 1_000)
                 .addHandler("Manager", 10_000)
                 .addHandler("Director", 100_000);

        // 800 is within TeamLead's limit, so the FIRST capable handler approves
        // it and the chain stops — no one else is even asked.
        Integer approvedBy = approvals.handle("laptop", 800);
        System.out.println("Approved with limit: " + approvedBy);
    }
}
