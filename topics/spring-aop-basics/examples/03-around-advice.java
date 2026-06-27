import visual.VisualAopProxy;

public class Playground {
    public static void main(String[] args) {
        VisualAopProxy app = new VisualAopProxy("CheckoutService");

        app.around("TimingAdvice", "*")
                .call("checkout")
                .targetLine("loadCart()")
                .targetLine("chargeCard()")
                .returnNormally();
    }
}
