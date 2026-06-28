import visual.VisualResource;

public class Playground {
    public static void main(String[] args) throws Exception {
        try (VisualResource connection = VisualResource.open("connection");
             VisualResource statement = VisualResource.open("statement")) {
            statement.use("execute query");
        }
    }
}
