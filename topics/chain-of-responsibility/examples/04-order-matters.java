import visual.VisualChain;

public class Playground {
    public static void main(String[] args) {
        // TRAP: order is part of the chain's behaviour. Here a broad "CEO"
        // handler with a huge limit is linked FIRST, so it approves everything —
        // every later, more specific handler becomes dead code and is never
        // reached. Put specific handlers before broad ones.
        VisualChain approvals = new VisualChain("approvals");
        approvals.addHandler("CEO", 1_000_000)   // too greedy, sits at the head
                 .addHandler("TeamLead", 1_000)
                 .addHandler("Manager", 10_000);

        // 800 could (and should) be approved by TeamLead, but CEO swallows it.
        Integer approvedBy = approvals.handle("laptop", 800);
        System.out.println("Approved with limit: " + approvedBy); // 1000000
    }
}
