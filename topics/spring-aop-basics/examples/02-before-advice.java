import visual.VisualAopProxy;

public class Playground {
    public static void main(String[] args) {
        VisualAopProxy app = new VisualAopProxy("OrderService");

        app.before("LoggingAdvice", "place*")
                .call("placeOrder")
                .targetLine("validateOrder()")
                .targetLine("saveOrder()")
                .returnNormally();
    }
}
