import visual.VisualAopProxy;

public class Playground {
    public static void main(String[] args) {
        VisualAopProxy app = new VisualAopProxy("InvoiceService");

        app.afterReturning("ReceiptAuditAdvice", "create*")
                .call("createInvoice")
                .targetLine("saveInvoice()")
                .returnNormally();
    }
}
