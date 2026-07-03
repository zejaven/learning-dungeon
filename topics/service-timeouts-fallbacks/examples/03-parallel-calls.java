import visual.VisualServiceCalls;

public class Playground {
    public static void main(String[] args) {
        VisualServiceCalls calls = new VisualServiceCalls("product-page");

        calls.deadline(250)
                .service("catalog", 70, true)
                .service("price", 90, true)
                .service("reviews", 2_000, false);

        calls.callParallel(150, "catalog", "price", "reviews");
        calls.completeResponse("PARTIAL_PRODUCT_PAGE");
    }
}
