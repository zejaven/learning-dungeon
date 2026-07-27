import visual.VisualWritePath;

public class Playground {
    public static void main(String[] args) {
        // "Batch your inserts" is the first thing everyone says. Measure it on
        // this table before believing it.
        VisualWritePath oneByOne = VisualWritePath.table("documents")
                .payload(30, false)
                .secondaryIndexes(4);
        oneByOne.insertRowByRow(4);
        oneByOne.report();

        // The same four records, one transaction, one executeBatch(): three
        // fewer round trips and three fewer fsyncs.
        VisualWritePath batched = VisualWritePath.table("documents")
                .payload(30, false)
                .secondaryIndexes(4);
        batched.insertBatch(4);
        batched.report();

        System.out.println("Batching removed 3 round trips and 3 fsyncs: 5.7 ms of a 598 ms write.");
        System.out.println("An optimisation can never be bigger than the slice it touches.");
    }
}
