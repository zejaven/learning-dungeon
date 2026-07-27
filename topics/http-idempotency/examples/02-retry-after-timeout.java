import visual.VisualIdempotency;
import visual.VisualIdempotency.Body;

public class Playground {
    public static void main(String[] args) {
        VisualIdempotency api = VisualIdempotency.serving("/orders",
                Body.of("item", "tea").and("qty", "2").and("status", "new"),
                Body.of("item", "cups").and("qty", "6").and("status", "paid"));

        // The reason the property exists at all: the network can eat the answer.
        api.dropNextResponse();
        Body paid = Body.of("item", "tea").and("qty", "2").and("status", "paid");
        api.put("/orders/1", paid);

        // The client saw a timeout. It cannot tell "my request never arrived"
        // from "the answer was lost", so it has to choose: resend, or give up.
        // Because PUT is idempotent, resending is the cheap, boring choice.
        api.retry();

        // And a safe read confirms the state the client wanted all along.
        api.get("/orders/1");

        api.report();
        System.out.println("A timeout is ambiguous. Idempotency is what makes the answer to it easy.");
    }
}
