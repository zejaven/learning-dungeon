import visual.VisualProxyFactory;

public class Playground {
    public static void main(String[] args) {
        // CGLIB intercepts by overriding methods in a subclass. A final method
        // cannot be overridden, so the call runs unadvised — no advice, no
        // transaction, no logging.
        new VisualProxyFactory("ReportService")
                .finalMethod("generate")
                .createProxy()
                .invoke("generate");
    }
}
