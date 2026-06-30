import java.util.function.Supplier;
import visual.VisualLambda;

public class Playground {
    private static final VisualLambda trace = new VisualLambda("Supplier<String>", "String get()");

    public static void main(String[] args) {
        trace.created("messageFactory", "() -> \"Order is ready\"");
        String message = buildMessage(() -> "Order is ready");
        System.out.println(message);
    }

    static String buildMessage(Supplier<String> messageFactory) {
        trace.passed("buildMessage", "messageFactory");
        return trace.invokeSupplier("messageFactory.get()", messageFactory);
    }
}
