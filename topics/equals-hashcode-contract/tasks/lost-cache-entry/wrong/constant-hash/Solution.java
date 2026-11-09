/** The key of a price cache: one SKU in one currency. */
final class PriceKey {
    private final String sku;
    private final String currency;
    /** Who created the entry. For diagnostics only, not part of the key's identity. */
    private final String note;

    PriceKey(String sku, String currency, String note) {
        this.sku = sku;
        this.currency = currency;
        this.note = note;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PriceKey other)) return false;
        return sku.equals(other.sku) && currency.equals(other.currency);
    }

    @Override
    public int hashCode() {
        return 1;
    }
}
