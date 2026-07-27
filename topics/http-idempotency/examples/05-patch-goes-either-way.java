import visual.VisualIdempotency;
import visual.VisualIdempotency.Body;

public class Playground {
    public static void main(String[] args) {
        VisualIdempotency api = VisualIdempotency.serving("/orders",
                Body.of("item", "tea").and("qty", "2").and("status", "new"),
                Body.of("item", "cups").and("qty", "6").and("status", "paid"));

        // A merge patch of ABSOLUTE values: "status is paid". Repeating it is a
        // no-op, so this PATCH endpoint happens to be idempotent.
        api.patchMerge("/orders/1", Body.of("status", "paid"));
        api.patchMerge("/orders/1", Body.of("status", "paid"));

        // The same method with a RELATIVE body: "add 1 to qty". The result now
        // depends on how many times it ran, which is why HTTP refuses to promise
        // idempotency for PATCH.
        api.patchIncrement("/orders/2", "qty", 1);
        api.patchIncrement("/orders/2", "qty", 1);

        api.report();
        System.out.println("Same method, opposite behaviour: the body decides, not the verb.");
    }
}
