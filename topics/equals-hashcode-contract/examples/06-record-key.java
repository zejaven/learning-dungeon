import visual.VisualEqualityContract;

public class Playground {
    public static void main(String[] args) {
        VisualEqualityContract<ProductKey> lab = new VisualEqualityContract<>("products");

        ProductKey first = new ProductKey("SKU-7", 3);
        ProductKey sameValue = new ProductKey("SKU-7", 3);

        lab.compare(first, sameValue);
        lab.add(first);
        lab.add(sameValue);
    }

    record ProductKey(String sku, int warehouseId) {
    }
}
