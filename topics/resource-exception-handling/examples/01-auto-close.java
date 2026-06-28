import visual.VisualResource;

public class Playground {
    public static void main(String[] args) throws Exception {
        try (VisualResource file = VisualResource.open("file")) {
            file.use("read customer.csv");
        }
    }
}
