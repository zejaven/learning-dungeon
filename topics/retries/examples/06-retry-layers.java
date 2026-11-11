import visual.VisualRetry;

import java.util.List;

public class Playground {
    public static void main(String[] args) {
        VisualRetry retry = new VisualRetry("one user request, database down");
        retry.maxAttempts(3);

        List<String> stack = List.of("gateway", "orders", "inventory", "db");

        // Every layer "helpfully" retries 3 times: 3 * 3 * 3 calls hit the database.
        int everywhere = retry.amplify(stack, List.of("gateway", "orders", "inventory"));

        // Only the layer closest to the failing dependency retries.
        int oneLayer = retry.amplify(stack, List.of("inventory"));

        System.out.println("db calls, retry everywhere = " + everywhere);
        System.out.println("db calls, retry at one layer = " + oneLayer);
    }
}
