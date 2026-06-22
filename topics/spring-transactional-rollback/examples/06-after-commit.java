import visual.VisualSpringTransaction;

public class Playground {
    public static void main(String[] args) {
        VisualSpringTransaction app = new VisualSpringTransaction("orders");

        // The transactional method finishes first, so the row is committed.
        app.transactional("createOrder")
                .persist("order-4", "PAID")
                .complete();

        // This failure happens after commit. It is outside that transaction, so
        // it cannot undo the already committed row.
        app.externalCallAfterCommitThrowsRuntime("emailClient", "EmailServiceDownException");

        System.out.println("The later external failure does not roll back order-4.");
    }
}
