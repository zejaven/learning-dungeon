// Client/director: it should orchestrate construction through OrderBuilder,
// not repeat a long Order constructor call in every workflow.
//
// TODO (missions):
//   1. Give OrderService a field of type OrderBuilder.
//   2. Use that builder in createDefaultOrder(...).
public class OrderService {

    public Order createDefaultOrder(String customer) {
        // Starter code compiles, but it is still tightly coupled to the product constructor.
        return new Order(customer, "standard package", false);
    }
}
