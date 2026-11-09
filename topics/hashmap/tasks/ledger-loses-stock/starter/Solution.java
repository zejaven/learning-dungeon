import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** A stock-keeping unit. Vendor names are case-insensitive: "ACME" and "Acme" are one vendor. */
final class Sku {
    private String vendor;
    private final String code;

    Sku(String vendor, String code) {
        this.vendor = vendor;
        this.code = code;
    }

    String vendor() {
        return vendor;
    }

    String code() {
        return code;
    }

    void rename(String newVendor) {
        this.vendor = newVendor;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Sku other)) return false;
        return vendor.equalsIgnoreCase(other.vendor) && code.equals(other.code);
    }

    @Override
    public int hashCode() {
        return Objects.hash(vendor, code);
    }

    @Override
    public String toString() {
        return vendor + "/" + code;
    }
}

/** Units in stock per SKU. */
final class StockLedger {
    private final Map<Sku, Integer> stock = new HashMap<>();

    void receive(Sku sku, int units) {
        stock.merge(sku, units, Integer::sum);
    }

    int unitsOf(Sku sku) {
        return stock.getOrDefault(sku, 0);
    }

    int distinctSkus() {
        return stock.size();
    }

    /** A vendor renamed itself: its SKUs keep their stock under the new name. */
    void rebrand(String oldVendor, String newVendor) {
        for (Sku sku : stock.keySet()) {
            if (sku.vendor().equalsIgnoreCase(oldVendor)) {
                sku.rename(newVendor);
            }
        }
    }

    /** "code=units" lines, sorted by code, for the nightly report. */
    List<String> report() {
        List<String> lines = new ArrayList<>();
        for (Map.Entry<Sku, Integer> e : stock.entrySet()) {
            lines.add(e.getKey().code() + "=" + e.getValue());
        }
        return lines;
    }
}
