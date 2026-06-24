// Builder contract: each method describes one construction step.
// TODO (missions): add a concrete DefaultOrderBuilder that implements this interface.
public interface OrderBuilder {
    OrderBuilder customer(String customer);

    OrderBuilder item(String item);

    OrderBuilder expedited(boolean expedited);

    Order build();
}
