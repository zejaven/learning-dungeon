import java.util.function.Consumer;
import visual.VisualLambda;

public class Playground {
    private static final VisualLambda trace = new VisualLambda("Consumer<String>", "void accept(String value)");

    public static void main(String[] args) {
        trace.created("printer", "orderId -> System.out.println(orderId)");
        withOrder("order-42", orderId -> System.out.println("Handling " + orderId));
    }

    static void withOrder(String orderId, Consumer<String> printer) {
        trace.passed("withOrder", "printer");
        trace.invokeConsumer("printer.accept(orderId)", orderId, printer);
    }
}
