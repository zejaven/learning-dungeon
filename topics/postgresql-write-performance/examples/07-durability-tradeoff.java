import visual.VisualWritePath;

public class Playground {
    public static void main(String[] args) {
        // Forty single-row transactions: forty commits, forty fsyncs.
        VisualWritePath durable = VisualWritePath.table("documents")
                .offloadPayload(512)
                .secondaryIndexes(2);
        durable.insertRowByRow(40);
        durable.report();

        // synchronous_commit = off. The commits stop waiting for the WAL to
        // reach the disk. A crash can lose the last few committed transactions
        // — the database is still consistent, and nothing is corrupted.
        VisualWritePath relaxed = VisualWritePath.table("documents")
                .offloadPayload(512)
                .secondaryIndexes(2)
                .asyncCommit();
        relaxed.insertRowByRow(40);
        relaxed.report();

        // The same fsync amortisation without giving up durability: fewer,
        // bigger transactions. Ten rows share one commit, and every commit
        // still waits for the disk.
        VisualWritePath grouped = VisualWritePath.table("documents")
                .offloadPayload(512)
                .secondaryIndexes(2);
        grouped.insertBatch(40, 10);
        grouped.report();

        System.out.println("Relaxing durability and batching attack the same fsync cost.");
        System.out.println("Batching is free; relaxing durability is a decision about lost data.");
    }
}
