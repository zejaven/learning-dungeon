import visual.VisualWritePath;

public class Playground {
    public static void main(String[] args) {
        // Many services call this API at once, each submitting one record.
        VisualWritePath naive = VisualWritePath.table("documents")
                .payload(30, false)
                .secondaryIndexes(4)
                .connectionPool(20);
        naive.serveApi(300);

        // The reflex: the pool is full, so make the pool bigger.
        VisualWritePath moreConnections = VisualWritePath.table("documents")
                .payload(30, false)
                .secondaryIndexes(4)
                .connectionPool(200);
        moreConnections.serveApi(300);

        // The fix that actually moves the ceiling: make each write cheaper.
        VisualWritePath fixed = VisualWritePath.table("documents")
                .payload(30, false)
                .secondaryIndexes(4)
                .offloadPayload(512)
                .connectionPool(20);
        fixed.serveApi(300);
        fixed.serveApi(15);

        System.out.println("Ten times the connections: the same 37.5 s, because the disk was the limit.");
        System.out.println("A cheaper write with the original 20 connections: 33.9 ms.");
    }
}
