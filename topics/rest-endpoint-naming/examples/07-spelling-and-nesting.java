import visual.VisualApiRoutes;

public class Playground {
    public static void main(String[] args) {
        VisualApiRoutes api = VisualApiRoutes.forService("Employee API");

        // The one everybody meant to write.
        api.expose("GET", "/employees/{id}");

        // Four ways to write "the same" URL -- and four different URLs.
        api.expose("GET", "/Employees/{id}");   // paths are case-sensitive
        api.expose("GET", "/employee/{id}");    // a collection, spelled singular
        api.expose("GET", "/employees.json");   // format baked into the name
        api.expose("GET", "/employees/");       // a trailing slash is a character

        // Every id on the way is a segment: reachable, but the document can only
        // be found by retracing its whole family tree.
        api.expose("GET", "/departments/{deptId}/employees/{id}/documents/{docId}");

        // A client that guessed the spelling gets nothing back.
        api.request("GET", "/Employee/42");

        api.review();
        System.out.println("One resource, five spellings: pick one convention and never vary it.");
    }
}
