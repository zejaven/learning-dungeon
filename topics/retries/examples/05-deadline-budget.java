import visual.VisualRetry;

public class Playground {
    public static void main(String[] args) {
        // A user request has 500 ms in total; retries must fit inside it.
        VisualRetry deadline = new VisualRetry("page -> recommendations");
        deadline.maxAttempts(5).backoff(100, 2.0, 1_000).deadline(500);
        deadline.script("503");
        deadline.call("GET /recommendations");

        // A retry budget: this client may spend only 2 retries in total.
        VisualRetry budget = new VisualRetry("reports -> warehouse");
        budget.maxAttempts(3).backoff(50, 2.0, 500).retryBudget(2);
        budget.script("503");
        budget.call("GET /stock/1");   // spends both tokens
        budget.call("GET /stock/2");   // budget empty: fails fast
    }
}
