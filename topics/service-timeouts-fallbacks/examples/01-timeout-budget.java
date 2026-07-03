import visual.VisualServiceCalls;

public class Playground {
    public static void main(String[] args) {
        VisualServiceCalls calls = new VisualServiceCalls("checkout");

        calls.deadline(300)
                .service("payment", 80, true)
                .service("inventory", 5_000, false);

        calls.call("payment", 100);
        calls.call("inventory", 120);
        calls.completeResponse("ORDER_ACCEPTED_WITHOUT_STOCK_HINT");
    }
}
