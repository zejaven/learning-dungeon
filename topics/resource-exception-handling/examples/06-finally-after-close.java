import visual.VisualResource;

public class Playground {
    public static void main(String[] args) throws Exception {
        try (VisualResource lock = VisualResource.open("lock")) {
            lock.use("update cache");
        } finally {
            VisualResource.runFinally("audit log");
        }
    }
}
