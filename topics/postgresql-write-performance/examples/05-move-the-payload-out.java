import visual.VisualWritePath;

public class Playground {
    public static void main(String[] args) {
        // Where we started: the payload is a column.
        VisualWritePath before = VisualWritePath.table("documents")
                .payload(30, false)
                .secondaryIndexes(4);
        before.insertRowByRow(4);
        before.report();

        // The design change. The payload goes to object storage (S3, MinIO, a
        // filesystem) and PostgreSQL keeps the row it is actually good at: an
        // id, an owner, a type, a URL, a checksum, a size and timestamps.
        VisualWritePath after = VisualWritePath.table("documents")
                .payload(30, false)
                .secondaryIndexes(4)
                .offloadPayload(512);
        after.explainPayload();
        after.insertRowByRow(4);
        after.report();

        System.out.println("Same four records, same four indexes, same one-INSERT-per-record code.");
        System.out.println("Nothing was tuned; the bytes simply stopped going through the WAL.");
    }
}
