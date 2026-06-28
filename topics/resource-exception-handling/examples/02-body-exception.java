import visual.VisualResource;

public class Playground {
    public static void main(String[] args) {
        try (VisualResource socket = VisualResource.open("socket")) {
            socket.failDuringUse("receive response", "response timeout");
        } catch (Exception e) {
            VisualResource.reportCaught(e);
        }
    }
}
