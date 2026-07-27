import visual.VisualWritePath;

public class Playground {
    public static void main(String[] args) {
        // "Drop some indexes, they slow down writes." True — but only relative
        // to what else the write is doing. Six indexes on a 30 MB row:
        VisualWritePath heavyRows = VisualWritePath.table("documents")
                .payload(30, false)
                .secondaryIndexes(6);
        heavyRows.explainIndexes();

        // The same six-plus indexes once the row is 512 B of metadata. Same
        // index work, completely different share of the write.
        VisualWritePath eightIndexes = VisualWritePath.table("documents")
                .offloadPayload(512)
                .secondaryIndexes(8);
        eightIndexes.explainIndexes();
        eightIndexes.insertBatch(40);
        eightIndexes.report();

        // The same 40 rows with only the index the write path really needs.
        VisualWritePath oneIndex = VisualWritePath.table("documents")
                .offloadPayload(512)
                .secondaryIndexes(1);
        oneIndex.explainIndexes();
        oneIndex.insertBatch(40);
        oneIndex.report();

        System.out.println("Index maintenance is invisible at 30 MB per row and a fifth of the write");
        System.out.println("at 512 B. Fix the biggest slice first, then this one becomes worth fixing.");
    }
}
