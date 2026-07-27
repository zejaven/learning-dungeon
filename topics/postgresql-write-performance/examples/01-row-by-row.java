import visual.VisualWritePath;

public class Playground {
    public static void main(String[] args) {
        // The service exactly as it was written: one INSERT per record, each in
        // its own transaction, each record carrying a 30 MB payload in a bytea
        // column. Four secondary indexes exist because other services query by
        // owner, type, status and created_at.
        VisualWritePath documents = VisualWritePath.table("documents")
                .payload(30, false)
                .secondaryIndexes(4);

        documents.insertRowByRow(4);
        documents.report();

        // Read the cost breakdown in the visualizer before reaching for a fix.
        System.out.println("Four records took ~600 ms. The round trips and the commits are 1% of it;");
        System.out.println("the other 99% is the 30 MB going to the table, to TOAST and to the WAL.");
    }
}
