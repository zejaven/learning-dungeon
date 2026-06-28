import visual.VisualResource;

public class Playground {
    public static void main(String[] args) {
        try (VisualResource printer = VisualResource.openFailingClose("printer")) {
            printer.use("print invoice");
        } catch (Exception e) {
            VisualResource.reportCaught(e);
        }
    }
}
