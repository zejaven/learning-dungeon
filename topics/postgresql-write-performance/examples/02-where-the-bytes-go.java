import visual.VisualWritePath;

public class Playground {
    public static void main(String[] args) {
        // A 30 MB value cannot live in the 8 KB page that holds its row.
        // PostgreSQL compresses it and slices what is left into ~2 KB chunk rows
        // in a side table, each chunk with its own index entry.
        VisualWritePath binary = VisualWritePath.table("documents").payload(30, false);
        binary.explainPayload();
        binary.explainWal();

        // The same size of payload, but text or JSON: the compressor gets
        // something back, so fewer bytes are stored, chunked and logged.
        VisualWritePath json = VisualWritePath.table("documents").payload(30, true);
        json.explainPayload();
        json.explainWal();

        // The payload is a JPEG / a zip / an encrypted blob. Compressing it is
        // pure CPU for nothing:
        //   ALTER TABLE documents ALTER COLUMN payload SET STORAGE EXTERNAL;
        VisualWritePath external = VisualWritePath.table("documents")
                .payload(30, false)
                .externalStorage();
        external.explainPayload();

        System.out.println("One INSERT of 30 MB is thousands of chunk rows plus ~30 MB of WAL,");
        System.out.println("and every replica and archive copies that WAL again.");
    }
}
