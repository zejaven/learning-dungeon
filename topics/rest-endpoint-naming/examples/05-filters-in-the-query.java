import visual.VisualApiRoutes;

public class Playground {
    public static void main(String[] args) {
        VisualApiRoutes api = VisualApiRoutes.forService("Employee API");

        api.expose("GET", "/employees");
        api.expose("GET", "/employees/{id}");
        api.expose("GET", "/employees/{id}/documents");

        // Four selections of the same collection, and still one URL: which
        // subset, in what order, on which page.
        api.request("GET", "/employees?department=finance&status=active&sort=-hiredAt&page=2");

        // The same list given a name of its own. It works -- and now "active" and
        // "sorted by hire date" each want a name too.
        api.expose("GET", "/employees/byDepartment/{name}");
        api.request("GET", "/employees/byDepartment/finance");

        api.review();
        System.out.println("A filter selects part of a collection; it does not create a new one.");
    }
}
