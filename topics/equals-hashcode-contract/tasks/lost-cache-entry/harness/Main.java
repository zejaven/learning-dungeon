import visual.TestKit;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Hidden tests for the PriceKey task. */
public class Main {

    public static void main(String[] args) {
        TestKit.run(
                TestKit.test("an equal key finds the stored price", 10, () -> {
                    Map<PriceKey, Integer> prices = new HashMap<>();
                    prices.put(new PriceKey("SKU-1", "USD", "import"), 10);
                    return prices.get(new PriceKey("SKU-1", "USD", "import"));
                }),
                TestKit.test("a set keeps one copy of equal keys", 1, () -> {
                    Set<PriceKey> keys = new HashSet<>();
                    keys.add(new PriceKey("SKU-1", "USD", "import"));
                    keys.add(new PriceKey("SKU-1", "USD", "import"));
                    return keys.size();
                }),
                TestKit.test("note does not change which key it is", 10, () -> {
                    Map<PriceKey, Integer> prices = new HashMap<>();
                    prices.put(new PriceKey("SKU-1", "USD", "import"), 10);
                    return prices.get(new PriceKey("SKU-1", "USD", "manual fix"));
                }),
                TestKit.test("another currency is another key", 2, () -> {
                    Map<PriceKey, Integer> prices = new HashMap<>();
                    prices.put(new PriceKey("SKU-1", "USD", "import"), 10);
                    prices.put(new PriceKey("SKU-1", "EUR", "import"), 9);
                    return prices.size();
                }),
                TestKit.test("different keys spread over many hash values", true, () -> {
                    Set<Integer> hashes = new HashSet<>();
                    for (int i = 0; i < 100; i++) {
                        hashes.add(new PriceKey("SKU-" + i, "USD", "import").hashCode());
                    }
                    return hashes.size() >= 50;
                })
        );
    }
}
