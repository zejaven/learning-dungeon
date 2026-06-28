import visual.VisualResource;

public class Playground {
    public static void main(String[] args) {
        try (VisualResource file = VisualResource.openFailingClose("file")) {
            file.failDuringUse("write report", "disk write failed");
        } catch (Exception e) {
            VisualResource.reportCaught(e);
        }
    }
}
