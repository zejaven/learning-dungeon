// Product: the object we want to create without a long constructor at every call site.
public class Order {
    private final String customer;
    private final String item;
    private final boolean expedited;

    public Order(String customer, String item, boolean expedited) {
        this.customer = customer;
        this.item = item;
        this.expedited = expedited;
    }

    public String customer() {
        return customer;
    }

    public String item() {
        return item;
    }

    public boolean expedited() {
        return expedited;
    }
}
