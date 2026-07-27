import visual.VisualWritePath;

public class Playground {
    public static void main(String[] args) {
        // Forty metadata rows, one INSERT and one commit each.
        VisualWritePath oneByOne = VisualWritePath.table("documents")
                .offloadPayload(512)
                .secondaryIndexes(2);
        oneByOne.insertRowByRow(40);
        oneByOne.report();

        // The same rows in batches of ten: four round trips and four commits
        // instead of forty of each.
        VisualWritePath batched = VisualWritePath.table("documents")
                .offloadPayload(512)
                .secondaryIndexes(2);
        batched.insertBatch(40, 10);
        batched.report();

        // COPY ... FROM STDIN streams the rows in one statement: the server
        // never parses or plans anything per row.
        VisualWritePath copied = VisualWritePath.table("documents")
                .offloadPayload(512)
                .secondaryIndexes(2);
        copied.copyIn(40);
        copied.report();

        System.out.println("The advice that bought 0.9% on the 30 MB table now buys ~80%.");
        System.out.println("The same fix is worth two different amounts depending on what dominates.");
    }
}
