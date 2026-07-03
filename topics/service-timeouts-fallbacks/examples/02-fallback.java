import visual.VisualServiceCalls;

public class Playground {
    public static void main(String[] args) {
        VisualServiceCalls calls = new VisualServiceCalls("product-page");

        calls.deadline(250)
                .service("recommendations", 4_000, false);

        calls.callWithFallback("recommendations", 100, "CACHE:POPULAR_PRODUCTS");
        calls.completeResponse("PAGE_WITH_FALLBACK_RECOMMENDATIONS");
    }
}
