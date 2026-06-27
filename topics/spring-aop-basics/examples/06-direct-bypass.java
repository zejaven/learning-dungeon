import visual.VisualAopProxy;

public class Playground {
    public static void main(String[] args) {
        VisualAopProxy app = new VisualAopProxy("OrderService");

        app.before("LoggingAdvice", "*")
                .directCall("placeOrder")
                .targetLine("validateOrder()")
                .targetLine("saveOrder()")
                .returnNormally();
    }
}
