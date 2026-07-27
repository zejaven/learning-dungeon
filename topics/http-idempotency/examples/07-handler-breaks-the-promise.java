import visual.VisualIdempotency;
import visual.VisualIdempotency.Body;

public class Playground {
    public static void main(String[] args) {
        VisualIdempotency api = VisualIdempotency.serving("/orders",
                Body.of("item", "tea").and("qty", "2").and("status", "new"),
                Body.of("item", "cups").and("qty", "6").and("status", "paid"));

        // PUT is idempotent by the spec. That is a promise about the ENDPOINT,
        // and it is the handler that has to keep it.
        Body shipped = Body.of("item", "tea").and("qty", "2").and("status", "shipped");
        api.dropNextResponse();
        api.putNotifying("/orders/1", shipped, "e-mail 'your order has shipped'");

        // The retry stores the same representation — and mails the customer again.
        // The row in the table is identical; the customer's inbox is not.
        api.retry();

        api.report();
        System.out.println("Idempotency covers every observable effect, not only the stored row.");
    }
}
