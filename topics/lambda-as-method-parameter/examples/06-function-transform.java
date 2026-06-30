import java.util.function.Function;
import visual.VisualLambda;

public class Playground {
    private static final VisualLambda trace = new VisualLambda(
            "Function<Integer, String>",
            "String apply(Integer value)");

    public static void main(String[] args) {
        trace.created("formatter", "cents -> \"$\" + cents / 100");
        String price = formatPrice(250, cents -> "$" + cents / 100);
        System.out.println(price);
    }

    static String formatPrice(int cents, Function<Integer, String> formatter) {
        trace.passed("formatPrice", "formatter");
        return trace.invokeFunction("formatter.apply(cents)", cents, formatter);
    }
}
