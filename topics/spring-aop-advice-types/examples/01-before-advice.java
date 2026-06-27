import visual.VisualAopProxy;

public class Playground {
    public static void main(String[] args) {
        VisualAopProxy app = new VisualAopProxy("PaymentService");

        app.before("PermissionCheckAdvice", "pay*")
                .call("payInvoice")
                .targetLine("chargeCard()")
                .returnNormally();
    }
}
